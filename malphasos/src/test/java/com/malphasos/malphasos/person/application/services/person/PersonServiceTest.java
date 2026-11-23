package com.malphasos.malphasos.person.application.services.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityProfile;
import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PhonePersonUseCaseRequest;
import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakEmailInUseException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PersonAlreadyHasAccountException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.domain.person.RoleType;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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

    /** Una persona del tipo que se pida, con lo mínimo para que `save` la acepte. */
    private Person personaDeTipo(PersonType tipo) {
        Person persona = new Person();
        persona.setCedula("1234567890");
        persona.setPrimerNombre("Ada");
        persona.setPrimerApellido("Lovelace");
        persona.setTipoPersona(tipo);

        return persona;
    }

    @Test
    @DisplayName("un registro sin telefonos se completa: la lista es opcional en el contrato")
    void registroSinTelefonos() {
        // Respondia 500: el servicio recorria la lista sin mirar si venia.
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PersonUseCaseRequest sinTelefonos = PersonUseCaseRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of(
                        EmailPersonUseCaseRequest.builder().correoPersona("ada@malphasos.local").build()))
                .build();

        Person creada = service().registerSupervisor(sinTelefonos);

        assertThat(creada.getPhonePersonList()).isEmpty();
        assertThat(creada.getEmailPersonList()).hasSize(1);
        verify(identityPort, never()).deleteUser(any());
    }

    @Test
    @DisplayName("un registro sin la lista de correos se rechaza diciendo por que, no con un nulo")
    void registroSinListaDeCorreos() {
        PersonUseCaseRequest sinCorreos = PersonUseCaseRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .build();

        assertThatThrownBy(() -> service().registerEngineer(sinCorreos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un correo");

        verify(identityPort, never()).createUser(any(PersonIdentityRequest.class), any());
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
        service.registerSupervisor(validRequest());

        verify(identityPort, org.mockito.Mockito.times(3)).createUser(any(), role.capture());
        assertThat(role.getAllValues())
                .containsExactly(RoleType.ENGINEER, RoleType.ADMIN, RoleType.SUPERVISOR);
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
    @DisplayName("dar de baja retira el acceso en Keycloak con el identificador de la persona")
    void deleteDisablesTheIdentityAccount() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete(id);

        verify(identityPort).disableUser(id.toString());
    }

    @Test
    @DisplayName("el orden falla cerrado: Keycloak se retira antes de guardar la baja")
    void deleteRevokesAccessBeforePersisting() {
        // Es la invariante de seguridad que corrige este cambio: si el orden se invierte, queda
        // una persona dada de baja que todavia puede entrar, que es el defecto original.
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete(id);

        InOrder orden = Mockito.inOrder(identityPort, persistencePort);
        orden.verify(identityPort).disableUser(id.toString());
        orden.verify(persistencePort).save(any());
    }

    @Test
    @DisplayName("si Keycloak no responde al dar de baja, se propaga el fallo y no se guarda")
    void deleteFailsClosedWhenKeycloakIsUnreachable() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new KeycloakConnectionException("keycloak caido"))
                .when(identityPort)
                .disableUser(id.toString());

        assertThatThrownBy(() -> service().delete(id))
                .isInstanceOf(KeycloakConnectionException.class);

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("sin permisos sobre Keycloak, la baja tambien se aborta")
    void deleteFailsClosedWhenKeycloakRejectsAuthorization() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new KeycloakUnauthorizedException("sin permisos"))
                .when(identityPort)
                .disableUser(id.toString());

        assertThatThrownBy(() -> service().delete(id))
                .isInstanceOf(KeycloakUnauthorizedException.class);

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("si la persona nunca tuvo cuenta en Keycloak, la baja se completa igual")
    void deleteCompletesWhenIdentityAccountIsMissing() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new KeycloakUserNotFoundException("no existe"))
                .when(identityPort)
                .disableUser(id.toString());

        service().delete(id);

        ArgumentCaptor<Person> saved = ArgumentCaptor.forClass(Person.class);
        verify(persistencePort).save(saved.capture());
        assertThat(saved.getValue().isEstadoActivo()).isFalse();
    }

    @Test
    @DisplayName("una persona ya inactiva vuelve a deshabilitarse en Keycloak al repetir la baja")
    void deleteRepeatsDisableUserForAnAlreadyInactivePerson() {
        // Intencional: repetir es barato y repara una desincronizacion si alguien reactivo la
        // cuenta a mano en la consola de Keycloak.
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(false)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete(id);

        verify(identityPort).disableUser(id.toString());
    }

    /** Una persona retirada, para reactivarla. */
    private Person retirada(UUID id) {
        return Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(false)
                .build();
    }

    @Test
    @DisplayName("reactivar vuelve a activar la ficha y habilita su cuenta (2026-11-22)")
    void reactivateRestoresThePersonAndItsAccount() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.of(retirada(id)));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Person reactivada = service().reactivate(id);

        assertThat(reactivada.isEstadoActivo()).isTrue();
        ArgumentCaptor<Person> saved = ArgumentCaptor.forClass(Person.class);
        verify(persistencePort).save(saved.capture());
        assertThat(saved.getValue().isEstadoActivo()).isTrue();
        verify(identityPort).enableUser(id.toString());
    }

    @Test
    @DisplayName("el orden falla cerrado: se guarda la ficha antes de habilitar la cuenta")
    void reactivatePersistsBeforeRestoringAccess() {
        // El contrario de la baja, por la misma razon: si se habilitara antes y el guardado fallara,
        // quedaria una cuenta abierta para una persona retirada.
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.of(retirada(id)));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().reactivate(id);

        InOrder orden = Mockito.inOrder(identityPort, persistencePort);
        orden.verify(persistencePort).save(any());
        orden.verify(identityPort).enableUser(id.toString());
    }

    @Test
    @DisplayName("si Keycloak no responde al reactivar, se propaga el fallo para deshacer la transaccion")
    void reactivateFailsClosedWhenKeycloakIsUnreachable() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.of(retirada(id)));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new KeycloakConnectionException("keycloak caido"))
                .when(identityPort)
                .enableUser(id.toString());

        assertThatThrownBy(() -> service().reactivate(id))
                .isInstanceOf(KeycloakConnectionException.class);
    }

    @Test
    @DisplayName("si la persona no tiene cuenta, se reactiva solo la ficha")
    void reactivateCompletesWhenIdentityAccountIsMissing() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.of(retirada(id)));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new KeycloakUserNotFoundException("no existe"))
                .when(identityPort)
                .enableUser(id.toString());

        assertThat(service().reactivate(id).isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("reactivar una persona inexistente no llama al proveedor de identidad")
    void reactivateOfUnknownPersonNeverTouchesIdentityPort() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reactivate(id)).isInstanceOf(PersonNotFoundException.class);

        verify(identityPort, never()).enableUser(any());
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("dar de baja una persona inexistente no llama al proveedor de identidad")
    void deleteOfUnknownPersonNeverTouchesIdentityPort() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(PersonNotFoundException.class);

        verify(identityPort, never()).disableUser(any());
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("actualizar propaga nombre y apellido a Keycloak antes de guardar")
    void updatePropagatesProfileBeforePersisting() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Person changes = Person.builder()
                .cedula("1234567890")
                .primerNombre("Grace")
                .primerApellido("Hopper")
                .tipoPersona(PersonType.ENGINEER)
                .build();

        service().update(id, changes);

        ArgumentCaptor<PersonIdentityProfile> profile = ArgumentCaptor.forClass(PersonIdentityProfile.class);
        InOrder orden = Mockito.inOrder(identityPort, persistencePort);
        orden.verify(identityPort).updateUserProfile(eq(id.toString()), profile.capture());
        orden.verify(persistencePort).save(any());
        assertThat(profile.getValue().firstName()).isEqualTo("Grace");
        assertThat(profile.getValue().lastName()).isEqualTo("Hopper");
    }

    @Test
    @DisplayName("actualizar no lleva a Keycloak ni el nombre de usuario ni el correo ni la contrasena")
    void updateProfileCarriesOnlyFirstAndLastName() {
        // Fija el contrato de PersonIdentityProfile: el record no tiene sitio para esos campos, y
        // esta prueba es la que deberia romperse si alguien lo amplia sin pensarlo.
        RecordComponent[] components = PersonIdentityProfile.class.getRecordComponents();

        assertThat(components)
                .extracting(RecordComponent::getName)
                .containsExactlyInAnyOrder("firstName", "lastName");
    }

    @Test
    @DisplayName("actualizar una persona sin cuenta en Keycloak persiste igual")
    void updatePersistsEvenWithoutAnIdentityAccount() {
        UUID id = UUID.randomUUID();
        Person existing = Person.builder()
                .identificador(id)
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        when(persistencePort.findById(id)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new KeycloakUserNotFoundException("no existe"))
                .when(identityPort)
                .updateUserProfile(eq(id.toString()), any());

        Person changes = Person.builder()
                .cedula("1234567890")
                .primerNombre("Grace")
                .primerApellido("Hopper")
                .tipoPersona(PersonType.ENGINEER)
                .build();

        Person updated = service().update(id, changes);

        assertThat(updated.getPrimerNombre()).isEqualTo("Grace");
        verify(persistencePort).save(any());
    }

    // ------------------------------------------------------------------------
    // Cambiar de tipo mueve la cuenta de grupo
    // ------------------------------------------------------------------------

    /** La persona que la base devuelve, del tipo que tenia antes de la edicion. */
    private UUID existeAlguienDeTipo(PersonType tipo) {
        UUID id = UUID.randomUUID();
        Person persona = personaDeTipo(tipo);
        persona.setIdentificador(id);
        when(persistencePort.findById(id)).thenReturn(Optional.of(persona));
        when(persistencePort.save(any())).thenAnswer(i -> i.getArgument(0));

        return id;
    }

    @Test
    @DisplayName("cambiar de supervisor a ingeniero mueve la cuenta al grupo de ingenieros")
    void cambiarDeTipoMueveDeGrupo() {
        // Hasta el 2026-10-04 esto no ocurria: quien dejaba de ser ingeniero conservaba sus
        // permisos, y un representante al que se le cambiaba el tipo perdia el filtrado por dueno
        // sin perder su grupo. La fila y la identidad decian cosas distintas.
        UUID id = existeAlguienDeTipo(PersonType.SUPERVISOR);

        service().update(id, personaDeTipo(PersonType.ENGINEER));

        verify(identityPort).syncGroup(id.toString(), RoleType.ENGINEER);
    }

    @Test
    @DisplayName("editar sin cambiar el tipo no toca el grupo")
    void editarSinCambiarTipoNoTocaElGrupo() {
        // No es correccion sino coste: el adaptador ya es idempotente. Editar un apellido no tiene
        // por que costar dos llamadas a Keycloak.
        UUID id = existeAlguienDeTipo(PersonType.ENGINEER);

        service().update(id, personaDeTipo(PersonType.ENGINEER));

        verify(identityPort, never()).syncGroup(any(), any());
    }

    @Test
    @DisplayName("un super usuario queda en el grupo de administradores, que es lo mas que un grupo da")
    void superUsuarioQuedaEnAdministradores() {
        // Ningun grupo del realm concede super.admin.full, a proposito: ese escalon se otorga a
        // mano. Dejarlo sin grupo dejaria sin acceso a quien se acaba de promover.
        UUID id = existeAlguienDeTipo(PersonType.ADMIN);

        service().update(id, personaDeTipo(PersonType.SUPER_ADMIN));

        verify(identityPort).syncGroup(id.toString(), RoleType.ADMIN);
    }

    @Test
    @DisplayName("que la persona no tenga cuenta no rompe la edicion")
    void sinCuentaNoRompe() {
        // Hasta V53 era el caso normal de un encargado; sus fichas pasaron a SUPERVISOR sin usuario. No
        // hay usuario que mover, y eso no es un error.
        UUID id = existeAlguienDeTipo(PersonType.SUPERVISOR);
        doThrow(new KeycloakUserNotFoundException("no existe"))
                .when(identityPort)
                .syncGroup(any(), any());

        Person resultado = service().update(id, personaDeTipo(PersonType.ENGINEER));

        assertThat(resultado.getTipoPersona()).isEqualTo(PersonType.ENGINEER);
    }

    // --- El correo de la cuenta (V40, 2026-10-17) -------------------------------------------

    private Person ingenieraConDosCorreos() {
        Person persona = Person.builder()
                .identificador(UUID.randomUUID())
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@viejo.local").estadoActivo(true).principal(true).build());
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@nuevo.local").estadoActivo(true).build());
        return persona;
    }

    @Test
    @DisplayName("el alta con cuenta marca como principal el correo que recibe Keycloak, que es el primero")
    void altaMarcaElPrimerCorreo() {
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<PersonIdentityRequest> aKeycloak = ArgumentCaptor.forClass(PersonIdentityRequest.class);
        PersonUseCaseRequest dosCorreos = PersonUseCaseRequest.builder()
                .cedula("1234567890").primerNombre("Ada").primerApellido("Lovelace")
                .nombreUsuario("ada").password("secreto")
                .emailPersonList(List.of(
                        EmailPersonUseCaseRequest.builder().correoPersona("primero@x.local").build(),
                        EmailPersonUseCaseRequest.builder().correoPersona("segundo@x.local").build()))
                .build();

        Person creada = service().registerEngineer(dosCorreos);

        verify(identityPort).createUser(aKeycloak.capture(), any());
        assertThat(creada.primaryEmail()).get().extracting(EmailPerson::getCorreoPersona)
                .isEqualTo(aKeycloak.getValue().email())
                .isEqualTo("primero@x.local");
        assertThat(creada.getEmailPersonList()).filteredOn(EmailPerson::isPrincipal).hasSize(1);
    }

    @Test
    @DisplayName("cambiar el correo de la cuenta lo lleva a Keycloak antes de guardar")
    void marcarPrincipalSincroniza() {
        Person persona = ingenieraConDosCorreos();
        EmailPerson nuevo = persona.getEmailPersonList().get(1);
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Person guardada = service().markPrimaryEmail(persona.getIdentificador(), nuevo.getIdCorreoPersona());

        InOrder orden = Mockito.inOrder(identityPort, persistencePort);
        orden.verify(identityPort).updateUserEmail(persona.getIdentificador().toString(), "ada@nuevo.local");
        orden.verify(persistencePort).save(persona);
        assertThat(guardada.primaryEmail()).get().isSameAs(nuevo);
    }

    @Test
    @DisplayName("si otra cuenta ya usa ese correo, no se guarda nada")
    void correoEnUsoNoGuarda() {
        Person persona = ingenieraConDosCorreos();
        EmailPerson nuevo = persona.getEmailPersonList().get(1);
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        doThrow(new KeycloakEmailInUseException("en uso", null))
                .when(identityPort).updateUserEmail(any(), any());

        assertThatThrownBy(() -> service().markPrimaryEmail(persona.getIdentificador(), nuevo.getIdCorreoPersona()))
                .isInstanceOf(KeycloakEmailInUseException.class);

        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("una persona cuya cuenta no está en Keycloak se marca igual: la ficha manda")
    void sinCuentaSeMarcaIgual() {
        Person persona = ingenieraConDosCorreos();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new KeycloakUserNotFoundException("no esta")).when(identityPort).updateUserEmail(any(), any());

        Person guardada = service().markPrimaryEmail(
                persona.getIdentificador(), persona.getEmailPersonList().get(1).getIdCorreoPersona());

        assertThat(guardada.primaryEmail()).get().extracting(EmailPerson::getCorreoPersona)
                .isEqualTo("ada@nuevo.local");
    }

    @Test
    @DisplayName("corregir el correo de la cuenta lo corrige también en Keycloak")
    void editarPrincipalSincroniza() {
        Person persona = ingenieraConDosCorreos();
        EmailPerson principal = persona.getEmailPersonList().getFirst();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateEmail(persona.getIdentificador(), principal.getIdCorreoPersona(),
                EmailPerson.builder().correoPersona("ada@corregido.local").build());

        verify(identityPort).updateUserEmail(persona.getIdentificador().toString(), "ada@corregido.local");
    }

    @Test
    @DisplayName("corregir otro correo no toca Keycloak, ni reescribir el principal con su mismo valor")
    void editarOtroNoSincroniza() {
        Person persona = ingenieraConDosCorreos();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().updateEmail(persona.getIdentificador(), persona.getEmailPersonList().get(1).getIdCorreoPersona(),
                EmailPerson.builder().correoPersona("otro@x.local").build());
        service().updateEmail(persona.getIdentificador(), persona.getEmailPersonList().getFirst().getIdCorreoPersona(),
                EmailPerson.builder().correoPersona("ada@viejo.local").build());

        verifyNoInteractions(identityPort);
    }

    // --- Restablecer la contraseña (2026-10-17) ---------------------------------------------

    @Test
    @DisplayName("restablecer la contraseña la pone en la cuenta y no toca la base")
    void restablecerContrasena() {
        Person persona = ingenieraConDosCorreos();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));

        service().resetPassword(persona.getIdentificador(), "temporal-de-prueba");

        verify(identityPort).resetPassword(persona.getIdentificador().toString(), "temporal-de-prueba");
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("a una persona retirada no se le restablece: su cuenta está deshabilitada a propósito")
    void restablecerRetirada() {
        Person persona = ingenieraConDosCorreos().setEstadoActivo(false);
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));

        assertThatThrownBy(() -> service().resetPassword(persona.getIdentificador(), "temporal-de-prueba"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(identityPort);
    }

    // --- Dar acceso a una ficha sin cuenta (2026-11-02) --------------------------------------

    /** Una ficha que era encargado: supervisora, sin cuenta, con un correo retirado delante del vigente. */
    private Person supervisoraSinCuenta() {
        Person persona = Person.builder()
                .identificador(UUID.randomUUID())
                .cedula("1234567890")
                .primerNombre("Grace")
                .primerApellido("Hopper")
                .tipoPersona(PersonType.SUPERVISOR)
                .estadoActivo(true)
                .build();
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("grace@retirado.local").estadoActivo(false).build());
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("grace@vigente.local").estadoActivo(true).build());
        return persona;
    }

    @Test
    @DisplayName("dar acceso crea el usuario con el primer correo vigente y el grupo de su tipo, y la ficha toma su identificador")
    void darAcceso() {
        Person persona = supervisoraSinCuenta();
        UUID viejo = persona.getIdentificador();
        UUID nuevo = UUID.randomUUID();
        Person conCuenta = new Person();
        when(persistencePort.findById(viejo)).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(viejo.toString())).thenReturn(Optional.empty());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(identityPort.createUser(any(), any())).thenReturn(nuevo.toString());
        when(persistencePort.changeIdentifier(viejo, nuevo)).thenReturn(conCuenta);
        ArgumentCaptor<PersonIdentityRequest> aKeycloak = ArgumentCaptor.forClass(PersonIdentityRequest.class);

        Person resultado = service().grantAccess(viejo, "grace", "temporal-de-prueba");

        assertThat(resultado).isSameAs(conCuenta);
        verify(identityPort).createUser(aKeycloak.capture(), eq(RoleType.SUPERVISOR));
        assertThat(aKeycloak.getValue().userName()).isEqualTo("grace");
        assertThat(aKeycloak.getValue().email()).isEqualTo("grace@vigente.local");
        assertThat(aKeycloak.getValue().password()).isEqualTo("temporal-de-prueba");
        assertThat(aKeycloak.getValue().firstName()).isEqualTo("Grace");
        assertThat(aKeycloak.getValue().lastName()).isEqualTo("Hopper");
        // El correo de la cuenta queda marcado ANTES de crear el usuario: los dos sistemas empiezan de acuerdo.
        InOrder orden = Mockito.inOrder(persistencePort, identityPort);
        orden.verify(persistencePort).save(persona);
        orden.verify(identityPort).createUser(any(), any());
        orden.verify(persistencePort).changeIdentifier(viejo, nuevo);
        assertThat(persona.getEmailPersonList().get(1).isPrincipal()).isTrue();
        assertThat(persona.getEmailPersonList().get(0).isPrincipal()).isFalse();
        verify(identityPort, never()).deleteUser(any());
    }

    @Test
    @DisplayName("si la ficha ya marca un correo de cuenta, ese es el que va, y no se guarda nada antes")
    void darAccesoConPrincipal() {
        Person persona = supervisoraSinCuenta();
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("grace@cuenta.local").estadoActivo(true).principal(true).build());
        UUID nuevo = UUID.randomUUID();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(any())).thenReturn(Optional.empty());
        when(identityPort.createUser(any(), any())).thenReturn(nuevo.toString());
        ArgumentCaptor<PersonIdentityRequest> aKeycloak = ArgumentCaptor.forClass(PersonIdentityRequest.class);

        service().grantAccess(persona.getIdentificador(), "grace", "temporal-de-prueba");

        verify(identityPort).createUser(aKeycloak.capture(), any());
        assertThat(aKeycloak.getValue().email()).isEqualTo("grace@cuenta.local");
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("el grupo es el del tipo de la ficha: un ingeniero sin cuenta entra como ingeniero")
    void darAccesoIngeniero() {
        Person persona = supervisoraSinCuenta().setTipoPersona(PersonType.ENGINEER);
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(any())).thenReturn(Optional.empty());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(identityPort.createUser(any(), any())).thenReturn(UUID.randomUUID().toString());

        service().grantAccess(persona.getIdentificador(), "grace", "temporal-de-prueba");

        verify(identityPort).createUser(any(), eq(RoleType.ENGINEER));
    }

    @Test
    @DisplayName("a quien ya tiene cuenta no se le crea otra")
    void darAccesoAQuienYaTiene() {
        Person persona = supervisoraSinCuenta();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(persona.getIdentificador().toString())).thenReturn(Optional.of("grace"));

        assertThatThrownBy(() -> service().grantAccess(persona.getIdentificador(), "grace", "temporal-de-prueba"))
                .isInstanceOf(PersonAlreadyHasAccountException.class);
        verify(identityPort, never()).createUser(any(), any());
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("a una persona retirada no se le da acceso")
    void darAccesoARetirada() {
        Person persona = supervisoraSinCuenta().setEstadoActivo(false);
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        // Estubado para que no sea «ya tiene cuenta» lo que la rechace.
        Mockito.lenient().when(identityPort.findUserName(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().grantAccess(persona.getIdentificador(), "grace", "temporal-de-prueba"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retirada");
        verify(identityPort, never()).createUser(any(), any());
    }

    @Test
    @DisplayName("sin ningún correo vigente no hay cuenta que crear, y se dice")
    void darAccesoSinCorreo() {
        Person persona = supervisoraSinCuenta();
        persona.getEmailPersonList().forEach(e -> e.setEstadoActivo(false));
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().grantAccess(persona.getIdentificador(), "grace", "temporal-de-prueba"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo");
        verify(identityPort, never()).createUser(any(), any());
    }

    @Test
    @DisplayName("si la ficha no toma el identificador nuevo, el usuario recién creado se borra")
    void darAccesoDeshaceElUsuario() {
        Person persona = supervisoraSinCuenta();
        UUID nuevo = UUID.randomUUID();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(any())).thenReturn(Optional.empty());
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(identityPort.createUser(any(), any())).thenReturn(nuevo.toString());
        when(persistencePort.changeIdentifier(any(), any())).thenThrow(new IllegalStateException("la base fallo"));

        assertThatThrownBy(() -> service().grantAccess(persona.getIdentificador(), "grace", "temporal-de-prueba"))
                .hasMessage("la base fallo");
        verify(identityPort).deleteUser(nuevo.toString());
    }

    @Test
    @DisplayName("la cuenta de una persona es su nombre de usuario, o nada si no la tiene")
    void cuentaDeUnaPersona() {
        Person persona = supervisoraSinCuenta();
        when(persistencePort.findById(persona.getIdentificador())).thenReturn(Optional.of(persona));
        when(identityPort.findUserName(persona.getIdentificador().toString()))
                .thenReturn(Optional.of("grace"), Optional.empty());

        assertThat(service().accountOf(persona.getIdentificador())).contains("grace");
        assertThat(service().accountOf(persona.getIdentificador())).isEmpty();
    }
}
