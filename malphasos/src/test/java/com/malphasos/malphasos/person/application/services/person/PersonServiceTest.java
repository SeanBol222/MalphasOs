package com.malphasos.malphasos.person.application.services.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PhonePersonUseCaseRequest;
import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.domain.person.RoleType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas de la orquestación del servicio, con los puertos de salida sustituidos por dobles. No
 * tocan base de datos ni Keycloak: verifican qué hace el servicio, no cómo se persiste.
 */
@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock private PersonPersistencePort persistencePort;
    @Mock private PersonIdentityPort identityPort;

    private PersonService servicio() {
        return new PersonService(persistencePort, identityPort);
    }

    private PersonUseCaseRequest peticionValida() {
        return PersonUseCaseRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of(
                        EmailPersonUseCaseRequest.builder().correoPersona("ada@malphasos.local").build()))
                .phonePersonList(List.of(
                        PhonePersonUseCaseRequest.builder().telefonoPersona("3001234567").build()))
                .build();
    }

    @Test
    @DisplayName("buscar una persona inexistente lanza PersonNotFoundException")
    void buscarPersonaInexistente() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().findById(id))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("el registro usa como identificador el que asigna el proveedor de identidad")
    void elIdentificadorProvieneDelProveedorDeIdentidad() {
        UUID idKeycloak = UUID.randomUUID();
        when(identityPort.createUser(any(), any())).thenReturn(idKeycloak.toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Person creada = servicio().registerEngineer(peticionValida());

        assertThat(creada.getIdentificador()).isEqualTo(idKeycloak);
        assertThat(creada.getTipoPersona()).isEqualTo(PersonType.ENGINEER);
        assertThat(creada.isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("cada metodo de registro pide el rol que le corresponde")
    void cadaRegistroUsaSuRol() {
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<RoleType> rol = ArgumentCaptor.forClass(RoleType.class);

        PersonService servicio = servicio();
        servicio.registerEngineer(peticionValida());
        servicio.registerAdmin(peticionValida());
        servicio.registerCeoClient(peticionValida());

        verify(identityPort, org.mockito.Mockito.times(3)).createUser(any(), rol.capture());
        assertThat(rol.getAllValues())
                .containsExactly(RoleType.ENGINEER, RoleType.ADMIN, RoleType.CEO_CLIENT);
    }

    @Test
    @DisplayName("el segundo tipo de persona llega a la persona creada")
    void elSegundoTipoNoSePierde() {
        // En el original este dato viajaba en la peticion pero nunca se trasladaba al modelo.
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PersonUseCaseRequest peticion = PersonUseCaseRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .segundoTipoPersona(PersonType.MANAGER)
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of(
                        EmailPersonUseCaseRequest.builder().correoPersona("ada@malphasos.local").build()))
                .phonePersonList(List.of())
                .build();

        Person creada = servicio().registerCeoClient(peticion);

        assertThat(creada.getSegundoTipoPersona()).isEqualTo(PersonType.MANAGER);
    }

    @Test
    @DisplayName("si la persistencia falla se elimina el usuario recien creado")
    void sePurgaElUsuarioSiFallaLaPersistencia() {
        String idUsuario = UUID.randomUUID().toString();
        when(identityPort.createUser(any(), any())).thenReturn(idUsuario);
        when(persistencePort.save(any())).thenThrow(new IllegalStateException("fallo la base"));

        assertThatThrownBy(() -> servicio().registerEngineer(peticionValida()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo la base");

        verify(identityPort).deleteUser(idUsuario);
    }

    @Test
    @DisplayName("si al deshacer tambien falla, prevalece el error original")
    void elErrorOriginalNoSePierdeSiFallaElRollback() {
        String idUsuario = UUID.randomUUID().toString();
        when(identityPort.createUser(any(), any())).thenReturn(idUsuario);
        when(persistencePort.save(any())).thenThrow(new IllegalStateException("fallo la base"));
        org.mockito.Mockito.doThrow(new IllegalStateException("keycloak caido"))
                .when(identityPort)
                .deleteUser(idUsuario);

        assertThatThrownBy(() -> servicio().registerEngineer(peticionValida()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo la base")
                .satisfies(e -> {
                    assertThat(e.getSuppressed()).hasSize(1);
                    assertThat(e.getSuppressed()[0]).hasMessageContaining("keycloak caido");
                });
    }

    @Test
    @DisplayName("un registro sin correo no llega a crear usuario en el proveedor de identidad")
    void sinCorreoNoSeCreaUsuario() {
        PersonUseCaseRequest sinCorreo = PersonUseCaseRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of())
                .phonePersonList(List.of())
                .build();

        assertThatThrownBy(() -> servicio().registerEngineer(sinCorreo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un correo");

        verify(identityPort, never()).createUser(any(PersonIdentityRequest.class), any());
    }

    @Test
    @DisplayName("eliminar una persona la desactiva en vez de borrarla")
    void eliminarEsBorradoLogico() {
        UUID id = UUID.randomUUID();
        Person existente = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existente));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio().delete(id);

        ArgumentCaptor<Person> guardada = ArgumentCaptor.forClass(Person.class);
        verify(persistencePort).save(guardada.capture());
        assertThat(guardada.getValue().isEstadoActivo()).isFalse();
        verify(persistencePort, never()).delete(any());
    }

    @Test
    @DisplayName("actualizar con una combinacion de tipos invalida no persiste nada")
    void actualizarConTiposInvalidosNoPersiste() {
        UUID id = UUID.randomUUID();
        Person existente = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existente));

        Person cambios = Person.builder()
                .tipoPersona(PersonType.ENGINEER)
                .segundoTipoPersona(PersonType.MANAGER)
                .build();

        assertThatThrownBy(() -> servicio().update(id, cambios))
                .isInstanceOf(IllegalArgumentException.class);

        verify(persistencePort, never()).save(any());
    }
}
