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

    private PersonService service() {
        return new PersonService(persistencePort, identityPort);
    }

    private PersonUseCaseRequest validRequest() {
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
    void findingUnknownPersonFails() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findById(id))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("el registro usa como identificador el que asigna el proveedor de identidad")
    void identifierComesFromIdentityProvider() {
        UUID keycloakId = UUID.randomUUID();
        when(identityPort.createUser(any(), any())).thenReturn(keycloakId.toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Person created = service().registerEngineer(validRequest());

        assertThat(created.getIdentificador()).isEqualTo(keycloakId);
        assertThat(created.getTipoPersona()).isEqualTo(PersonType.ENGINEER);
        assertThat(created.isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("cada metodo de registro pide el rol que le corresponde")
    void eachRegistrationUsesItsRole() {
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<RoleType> role = ArgumentCaptor.forClass(RoleType.class);

        PersonService service = service();
        service.registerEngineer(validRequest());
        service.registerAdmin(validRequest());
        service.registerCeoClient(validRequest());

        verify(identityPort, org.mockito.Mockito.times(3)).createUser(any(), role.capture());
        assertThat(role.getAllValues())
                .containsExactly(RoleType.ENGINEER, RoleType.ADMIN, RoleType.CEO_CLIENT);
    }

    @Test
    @DisplayName("el segundo tipo de persona llega a la persona creada")
    void secondTypeIsNotLost() {
        // En el original este dato viajaba en la peticion pero nunca se trasladaba al modelo.
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PersonUseCaseRequest request = PersonUseCaseRequest.builder()
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

        Person created = service().registerCeoClient(request);

        assertThat(created.getSegundoTipoPersona()).isEqualTo(PersonType.MANAGER);
    }

    @Test
    @DisplayName("si la persistencia falla se elimina el usuario recien creado")
    void userIsRemovedWhenPersistenceFails() {
        String userId = UUID.randomUUID().toString();
        when(identityPort.createUser(any(), any())).thenReturn(userId);
        when(persistencePort.save(any())).thenThrow(new IllegalStateException("fallo la base"));

        assertThatThrownBy(() -> service().registerEngineer(validRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo la base");

        verify(identityPort).deleteUser(userId);
    }

    @Test
    @DisplayName("si al deshacer tambien falla, prevalece el error original")
    void originalErrorSurvivesFailedRollback() {
        String userId = UUID.randomUUID().toString();
        when(identityPort.createUser(any(), any())).thenReturn(userId);
        when(persistencePort.save(any())).thenThrow(new IllegalStateException("fallo la base"));
        org.mockito.Mockito.doThrow(new IllegalStateException("keycloak caido"))
                .when(identityPort)
                .deleteUser(userId);

        assertThatThrownBy(() -> service().registerEngineer(validRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo la base")
                .satisfies(e -> {
                    assertThat(e.getSuppressed()).hasSize(1);
                    assertThat(e.getSuppressed()[0]).hasMessageContaining("keycloak caido");
                });
    }

    @Test
    @DisplayName("un registro sin correo no llega a crear usuario en el proveedor de identidad")
    void noUserIsCreatedWithoutEmail() {
        PersonUseCaseRequest withoutEmail = PersonUseCaseRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of())
                .phonePersonList(List.of())
                .build();

        assertThatThrownBy(() -> service().registerEngineer(withoutEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un correo");

        verify(identityPort, never()).createUser(any(PersonIdentityRequest.class), any());
    }

    @Test
    @DisplayName("eliminar una persona la desactiva en vez de borrarla")
    void deleteIsSoftDelete() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete(id);

        // El puerto de persistencia ya no ofrece ninguna operacion de borrado, de modo que la
        // ausencia de borrado fisico esta garantizada por el propio contrato.
        ArgumentCaptor<Person> saved = ArgumentCaptor.forClass(Person.class);
        verify(persistencePort).save(saved.capture());
        assertThat(saved.getValue().isEstadoActivo()).isFalse();
    }

    @Test
    @DisplayName("actualizar con una combinacion de tipos invalida no persiste nada")
    void updateWithInvalidTypesDoesNotPersist() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));

        Person changes = Person.builder()
                .tipoPersona(PersonType.ENGINEER)
                .segundoTipoPersona(PersonType.MANAGER)
                .build();

        assertThatThrownBy(() -> service().update(id, changes))
                .isInstanceOf(IllegalArgumentException.class);

        verify(persistencePort, never()).save(any());
    }
}
