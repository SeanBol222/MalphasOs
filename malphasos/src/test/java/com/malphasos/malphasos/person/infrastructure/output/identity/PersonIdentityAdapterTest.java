package com.malphasos.malphasos.person.infrastructure.output.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakInvalidDataException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.person.RoleType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Verifica la traducción entre la Admin API de Keycloak y el dominio, con el cliente sustituido por
 * dobles: no requiere un Keycloak en ejecución.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonIdentityAdapterTest {

    private static final String REALM = "malphasos-realm";

    @Mock private Keycloak keycloak;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private Response response;

    private PersonIdentityAdapter adaptador;

    @BeforeEach
    void prepararCliente() {
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any())).thenReturn(response);
        adaptador = new PersonIdentityAdapter(keycloak, REALM);
    }

    private PersonIdentityRequest peticion() {
        return PersonIdentityRequest.builder()
                .userName("ada")
                .email("ada@malphasos.local")
                .firstName("Ada")
                .lastName("Lovelace")
                .password("secreto")
                .build();
    }

    @Test
    @DisplayName("un alta correcta devuelve el identificador que viene en la cabecera Location")
    void devuelveElIdDelUsuarioCreado() {
        UUID idEsperado = UUID.randomUUID();
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://keycloak/admin/realms/" + REALM + "/users/" + idEsperado));

        String id = adaptador.createUser(peticion(), RoleType.ENGINEER);

        assertThat(id).isEqualTo(idEsperado.toString());
    }

    @Test
    @DisplayName("la respuesta se cierra siempre, tambien cuando el alta falla")
    void laRespuestaSeCierra() {
        // El original nunca cerraba el Response, de modo que cada alta dejaba una conexion retenida.
        when(response.getStatus()).thenReturn(409);

        assertThatThrownBy(() -> adaptador.createUser(peticion(), RoleType.ENGINEER))
                .isInstanceOf(KeycloakUserAlreadyExistsException.class);

        verify(response).close();
    }

    @Test
    @DisplayName("un conflicto se traduce a la excepcion de usuario ya existente")
    void conflictoSeTraduce() {
        when(response.getStatus()).thenReturn(409);

        assertThatThrownBy(() -> adaptador.createUser(peticion(), RoleType.ADMIN))
                .isInstanceOf(KeycloakUserAlreadyExistsException.class)
                .hasMessageContaining(REALM);
    }

    @Test
    @DisplayName("datos rechazados se traducen a la excepcion de datos invalidos")
    void datosInvalidosSeTraducen() {
        when(response.getStatus()).thenReturn(400);

        assertThatThrownBy(() -> adaptador.createUser(peticion(), RoleType.ADMIN))
                .isInstanceOf(KeycloakInvalidDataException.class);
    }

    @ParameterizedTest(name = "el codigo {0} indica falta de permisos")
    @CsvSource({"401", "403"})
    void faltaDePermisosSeTraduce(int codigo) {
        when(response.getStatus()).thenReturn(codigo);

        assertThatThrownBy(() -> adaptador.createUser(peticion(), RoleType.ADMIN))
                .isInstanceOf(KeycloakUnauthorizedException.class);
    }

    @Test
    @DisplayName("cualquier otro codigo se traduce a un fallo de comunicacion")
    void otroCodigoSeTraduce() {
        when(response.getStatus()).thenReturn(503);

        assertThatThrownBy(() -> adaptador.createUser(peticion(), RoleType.ADMIN))
                .isInstanceOf(KeycloakConnectionException.class)
                .hasMessageContaining("503");
    }

    @ParameterizedTest(name = "el rol {0} se asigna al grupo {1}")
    @CsvSource({"ENGINEER,engineers", "CEO_CLIENT,clients", "ADMIN,admins"})
    void cadaRolSeAsignaASuGrupo(RoleType rol, String grupoEsperado) {
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://keycloak/users/" + UUID.randomUUID()));

        adaptador.createUser(peticion(), rol);

        ArgumentCaptor<UserRepresentation> usuario = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(usuario.capture());
        assertThat(usuario.getValue().getGroups()).containsExactly(grupoEsperado);
    }

    @Test
    @DisplayName("el usuario se crea habilitado y con su credencial")
    void elUsuarioSeCreaUsable() {
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://keycloak/users/" + UUID.randomUUID()));

        adaptador.createUser(peticion(), RoleType.ENGINEER);

        ArgumentCaptor<UserRepresentation> usuario = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(usuario.capture());
        assertThat(usuario.getValue().isEnabled()).isTrue();
        assertThat(usuario.getValue().getUsername()).isEqualTo("ada");
        assertThat(usuario.getValue().getCredentials()).hasSize(1);
    }

    @Test
    @DisplayName("un fallo al eliminar conserva la excepcion original como causa")
    void elBorradoConservaLaCausa() {
        // El original envolvia el fallo concatenando el mensaje, perdiendo la traza original.
        String idUsuario = UUID.randomUUID().toString();
        RuntimeException causaReal = new IllegalStateException("conexion rechazada");
        when(usersResource.delete(idUsuario)).thenThrow(causaReal);

        assertThatThrownBy(() -> adaptador.deleteUser(idUsuario))
                .isInstanceOf(KeycloakConnectionException.class)
                .hasCause(causaReal);
    }
}
