package com.malphasos.malphasos.person.infrastructure.output.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityProfile;
import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakInvalidDataException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserNotFoundException;
import com.malphasos.malphasos.person.domain.person.RoleType;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
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
    @Mock private UserResource userResource;
    @Mock private Response response;

    private PersonIdentityAdapter adapter;

    @BeforeEach
    void prepareClient() {
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any())).thenReturn(response);
        adapter = new PersonIdentityAdapter(keycloak, REALM);
    }

    private PersonIdentityRequest request() {
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
    void returnsCreatedUserId() {
        UUID expectedId = UUID.randomUUID();
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://keycloak/admin/realms/" + REALM + "/users/" + expectedId));

        String id = adapter.createUser(request(), RoleType.ENGINEER);

        assertThat(id).isEqualTo(expectedId.toString());
    }

    @Test
    @DisplayName("la respuesta se cierra siempre, tambien cuando el alta falla")
    void responseIsAlwaysClosed() {
        // El original nunca cerraba el Response, de modo que cada alta dejaba una conexion retenida.
        when(response.getStatus()).thenReturn(409);

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ENGINEER))
                .isInstanceOf(KeycloakUserAlreadyExistsException.class);

        verify(response).close();
    }

    @Test
    @DisplayName("un conflicto se traduce a la excepcion de usuario ya existente")
    void conflictIsTranslated() {
        when(response.getStatus()).thenReturn(409);

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ADMIN))
                .isInstanceOf(KeycloakUserAlreadyExistsException.class)
                .hasMessageContaining(REALM);
    }

    @Test
    @DisplayName("datos rechazados se traducen a la excepcion de datos invalidos")
    void invalidDataIsTranslated() {
        when(response.getStatus()).thenReturn(400);

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ADMIN))
                .isInstanceOf(KeycloakInvalidDataException.class);
    }

    @ParameterizedTest(name = "el codigo {0} indica falta de permisos")
    @CsvSource({"401", "403"})
    void missingPermissionsIsTranslated(int status) {
        when(response.getStatus()).thenReturn(status);

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ADMIN))
                .isInstanceOf(KeycloakUnauthorizedException.class);
    }

    @Test
    @DisplayName("cualquier otro codigo se traduce a un fallo de comunicacion")
    void otherStatusIsTranslated() {
        when(response.getStatus()).thenReturn(503);

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ADMIN))
                .isInstanceOf(KeycloakConnectionException.class)
                .hasMessageContaining("503");
    }

    @ParameterizedTest(name = "el rol {0} se asigna al grupo {1}")
    @CsvSource({"ENGINEER,engineers", "CEO_CLIENT,clients", "ADMIN,admins"})
    void eachRoleMapsToItsGroup(RoleType role, String expectedGroup) {
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://keycloak/users/" + UUID.randomUUID()));

        adapter.createUser(request(), role);

        ArgumentCaptor<UserRepresentation> user = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(user.capture());
        assertThat(user.getValue().getGroups()).containsExactly(expectedGroup);
    }

    @Test
    @DisplayName("el usuario se crea habilitado y con su credencial")
    void userIsCreatedEnabled() {
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://keycloak/users/" + UUID.randomUUID()));

        adapter.createUser(request(), RoleType.ENGINEER);

        ArgumentCaptor<UserRepresentation> user = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(user.capture());
        assertThat(user.getValue().isEnabled()).isTrue();
        assertThat(user.getValue().getUsername()).isEqualTo("ada");
        assertThat(user.getValue().getCredentials()).hasSize(1);
    }

    @Test
    @DisplayName("si el cliente no logra autenticarse contra Keycloak, se traduce a excepcion de dominio")
    void clientAuthFailureIsTranslated() {
        // Regresion: el cliente lanza antes de devolver un Response cuando el secreto del client
        // administrativo falta o no coincide. Sin traducirlo, la excepcion escapaba hasta el
        // servlet y el llamante recibia un 500 generico fuera del contrato de errores.
        when(usersResource.create(any()))
                .thenThrow(new ProcessingException(new NotAuthorizedException("HTTP 401 Unauthorized")));

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ENGINEER))
                .isInstanceOf(KeycloakUnauthorizedException.class)
                .hasMessageContaining("secreto");
    }

    @Test
    @DisplayName("un fallo de red al crear se traduce a error de comunicacion")
    void networkFailureIsTranslated() {
        when(usersResource.create(any()))
                .thenThrow(new ProcessingException(new java.net.ConnectException("conexion rechazada")));

        assertThatThrownBy(() -> adapter.createUser(request(), RoleType.ENGINEER))
                .isInstanceOf(KeycloakConnectionException.class);
    }

    @Test
    @DisplayName("un fallo al eliminar conserva la excepcion original como causa")
    void deleteKeepsOriginalCause() {
        // El original envolvia el fallo concatenando el mensaje, perdiendo la traza original.
        String userId = UUID.randomUUID().toString();
        RuntimeException actualCause = new IllegalStateException("conexion rechazada");
        when(usersResource.delete(userId)).thenThrow(actualCause);

        assertThatThrownBy(() -> adapter.deleteUser(userId))
                .isInstanceOf(KeycloakConnectionException.class)
                .hasCause(actualCause);
    }

    @Test
    @DisplayName("deshabilitar envia la representacion leida, no una construida desde cero")
    void disableUserSendsTheRepresentationItRead() {
        String userId = UUID.randomUUID().toString();
        UserRepresentation existing = new UserRepresentation();
        existing.setId(userId);
        existing.setUsername("ada");
        existing.setEmail("ada@malphasos.local");
        existing.setEnabled(true);
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(existing);

        adapter.disableUser(userId);

        ArgumentCaptor<UserRepresentation> sent = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(sent.capture());
        assertThat(sent.getValue()).isSameAs(existing);
        assertThat(sent.getValue().isEnabled()).isFalse();
        assertThat(sent.getValue().getUsername()).isEqualTo("ada");
        assertThat(sent.getValue().getEmail()).isEqualTo("ada@malphasos.local");
    }

    @Test
    @DisplayName("actualizar el perfil solo cambia nombre y apellido")
    void updateUserProfileTouchesOnlyNameFields() {
        String userId = UUID.randomUUID().toString();
        UserRepresentation existing = new UserRepresentation();
        existing.setId(userId);
        existing.setUsername("ada");
        existing.setEmail("ada@malphasos.local");
        existing.setFirstName("Ada");
        existing.setLastName("Lovelace");
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(existing);

        adapter.updateUserProfile(
                userId, PersonIdentityProfile.builder().firstName("Grace").lastName("Hopper").build());

        ArgumentCaptor<UserRepresentation> sent = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(sent.capture());
        assertThat(sent.getValue().getFirstName()).isEqualTo("Grace");
        assertThat(sent.getValue().getLastName()).isEqualTo("Hopper");
        assertThat(sent.getValue().getUsername()).isEqualTo("ada");
        assertThat(sent.getValue().getEmail()).isEqualTo("ada@malphasos.local");
        assertThat(sent.getValue().getCredentials()).isNull();
    }

    @ParameterizedTest(name = "un usuario inexistente al {0} se traduce a KeycloakUserNotFoundException")
    @ValueSource(strings = {"disable", "update"})
    void missingUserIsTranslatedOnBothOperations(String operation) {
        // Regresion del case 404 nuevo en translateClientFailure: debe cubrir las dos operaciones
        // que lo introdujeron sin alterar lo que ya traducian createUser y deleteUser.
        String userId = UUID.randomUUID().toString();
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation())
                .thenThrow(new ProcessingException(new NotFoundException("HTTP 404 Not Found")));

        Runnable call = "disable".equals(operation)
                ? () -> adapter.disableUser(userId)
                : () -> adapter.updateUserProfile(
                        userId, PersonIdentityProfile.builder().firstName("Grace").build());

        assertThatThrownBy(call::run).isInstanceOf(KeycloakUserNotFoundException.class);
    }

    @Test
    @DisplayName("sin permisos al leer el usuario, deshabilitar se traduce a fallo de autorizacion")
    void disableUserWithoutPermissionsIsTranslated() {
        String userId = UUID.randomUUID().toString();
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation())
                .thenThrow(new ProcessingException(new NotAuthorizedException("HTTP 401 Unauthorized")));

        assertThatThrownBy(() -> adapter.disableUser(userId))
                .isInstanceOf(KeycloakUnauthorizedException.class);
    }

    @Test
    @DisplayName("un fallo de red al actualizar el perfil se traduce a error de comunicacion")
    void updateUserProfileNetworkFailureIsTranslated() {
        String userId = UUID.randomUUID().toString();
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation())
                .thenThrow(new ProcessingException(new java.net.ConnectException("conexion rechazada")));

        assertThatThrownBy(() -> adapter.updateUserProfile(
                        userId, PersonIdentityProfile.builder().firstName("Grace").build()))
                .isInstanceOf(KeycloakConnectionException.class);
    }

    @Test
    @DisplayName("un fallo inesperado al deshabilitar conserva la causa original")
    void disableUserWrapsUnexpectedFailureWithCause() {
        String userId = UUID.randomUUID().toString();
        RuntimeException actualCause = new IllegalStateException("conexion rechazada");
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenThrow(actualCause);

        assertThatThrownBy(() -> adapter.disableUser(userId))
                .isInstanceOf(KeycloakConnectionException.class)
                .hasCause(actualCause);
    }

    @Test
    @DisplayName("eliminar sigue traduciendo un 404 de respuesta como fallo de conexion, sin cambios")
    void deleteStillTranslatesResponseNotFoundAsBefore() {
        // Regresion del case 404 nuevo: deleteUser interpreta un 404 en dos sitios distintos. Este
        // camino -un Response con status 404, no una excepcion del cliente- no pasa por
        // translateClientFailure, y el case 404 anadido para disableUser/updateUserProfile no debe
        // tocarlo: sigue siendo un fallo de conexion generico, como antes del cambio.
        when(response.getStatus()).thenReturn(404);
        String userId = UUID.randomUUID().toString();
        when(usersResource.delete(userId)).thenReturn(response);

        assertThatThrownBy(() -> adapter.deleteUser(userId))
                .isInstanceOf(KeycloakConnectionException.class)
                .isNotInstanceOf(KeycloakUserNotFoundException.class);
    }

    @Test
    @DisplayName("eliminar tambien se beneficia del case 404 cuando el cliente lanza en vez de responder")
    void deleteTranslatesClientThrownNotFound() {
        // A diferencia de la prueba anterior, aqui el cliente lanza antes de completar la llamada
        // -el mismo camino que ya cubria deleteKeepsOriginalCause- y ese camino si pasa por
        // translateClientFailure, de modo que ahora tambien reconoce el 404.
        String userId = UUID.randomUUID().toString();
        when(usersResource.delete(userId))
                .thenThrow(new ProcessingException(new NotFoundException("HTTP 404 Not Found")));

        assertThatThrownBy(() -> adapter.deleteUser(userId))
                .isInstanceOf(KeycloakUserNotFoundException.class);
    }
}
