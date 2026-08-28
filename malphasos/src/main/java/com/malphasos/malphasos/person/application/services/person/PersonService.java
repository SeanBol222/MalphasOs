package com.malphasos.malphasos.person.application.services.person;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import com.malphasos.malphasos.person.domain.person.RoleType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso sobre personas: coordina el modelo de dominio, el almacenamiento y el
 * proveedor de identidad, sin contener reglas de negocio propias.
 *
 * <p>Depende únicamente de los puertos. En el proyecto original inyectaba directamente la clase
 * concreta del adaptador de Keycloak, dejando sin uso el puerto que existía para eso y acoplando la
 * capa de aplicación a una tecnología concreta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonService implements PersonServicePort {

    private final PersonPersistencePort personPersistencePort;
    private final PersonIdentityPort personIdentityPort;

    @Override
    @Transactional(readOnly = true)
    public Person findById(UUID id) {
        return personPersistencePort.findById(id).orElseThrow(() -> new PersonNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Person> findAll() {
        return personPersistencePort.findAll();
    }

    @Override
    @Transactional
    public Person save(Person person) {

        person.validarRoles();

        person.setIdentificador(UUID.randomUUID());
        person.setEstadoActivo(true);

        person.getPhonePersonList().forEach(phone -> {
            phone.setIdTelefonoPersona(UUID.randomUUID());
            phone.setEstadoActivo(true);
        });
        person.getEmailPersonList().forEach(email -> {
            email.setIdCorreoPersona(UUID.randomUUID());
            email.setEstadoActivo(true);
        });

        return personPersistencePort.save(person);
    }

    @Override
    public Person registerEngineer(PersonUseCaseRequest request) {
        return register(request, PersonType.ENGINEER, RoleType.ENGINEER);
    }

    @Override
    public Person registerAdmin(PersonUseCaseRequest request) {
        return register(request, PersonType.ADMIN, RoleType.ADMIN);
    }

    @Override
    public Person registerCeoClient(PersonUseCaseRequest request) {
        return register(request, PersonType.CEO_CLIENT, RoleType.CEO_CLIENT);
    }

    /**
     * Alta de una persona con acceso al sistema.
     *
     * <p>Escribe en dos sistemas que no comparten transacción: primero el usuario en el proveedor de
     * identidad y después la persona en la base de datos. Si la persistencia falla, se elimina el
     * usuario recién creado para no dejar cuentas huérfanas.
     *
     * <p>El identificador de la persona es el que asigna el proveedor de identidad, de modo que
     * ambos sistemas comparten la misma clave y no hace falta una tabla de correspondencia.
     *
     * <p>En el original este método estaba escrito tres veces, una por rol, con cuerpos idénticos
     * salvo el valor del rol.
     */
    private Person register(PersonUseCaseRequest request, PersonType tipoPersona, RoleType roleType) {

        String idUsuario = personIdentityPort.createUser(identityRequestFrom(request), roleType);

        try {
            Person person = personFrom(request, tipoPersona, UUID.fromString(idUsuario));
            person.validarRoles();

            return personPersistencePort.save(person);

        } catch (RuntimeException fallo) {
            deshacerUsuario(idUsuario, fallo);
            throw fallo;
        }
    }

    /**
     * Elimina el usuario creado cuando el resto del alta falló.
     *
     * <p>Si la eliminación también falla, se adjunta como excepción suprimida en lugar de
     * reemplazar al error original: quien depure necesita ver la causa real del fallo, y además
     * saber que quedó un usuario huérfano.
     */
    private void deshacerUsuario(String idUsuario, RuntimeException fallo) {
        try {
            personIdentityPort.deleteUser(idUsuario);
        } catch (RuntimeException fallaAlDeshacer) {
            log.error(
                    "No se pudo eliminar el usuario {} tras fallar el registro. Queda huerfano en el "
                            + "proveedor de identidad.",
                    idUsuario,
                    fallaAlDeshacer);
            fallo.addSuppressed(fallaAlDeshacer);
        }
    }

    @Override
    @Transactional
    public Person update(UUID id, Person person) {
        return personPersistencePort.findById(id)
                .map(existente -> {
                    existente.setCedula(person.getCedula());
                    existente.setPrimerNombre(person.getPrimerNombre());
                    existente.setSegundoNombre(person.getSegundoNombre());
                    existente.setPrimerApellido(person.getPrimerApellido());
                    existente.setSegundoApellido(person.getSegundoApellido());
                    existente.setTipoPersona(person.getTipoPersona());
                    existente.setSegundoTipoPersona(person.getSegundoTipoPersona());

                    existente.validarRoles();

                    return personPersistencePort.save(existente);
                })
                .orElseThrow(() -> new PersonNotFoundException(id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Person persona = personPersistencePort.findById(id)
                .orElseThrow(() -> new PersonNotFoundException(id));

        persona.setEstadoActivo(false);
        personPersistencePort.save(persona);
    }

    @Override
    @Transactional
    public Person addEmail(UUID personId, EmailPerson email) {
        Person persona = buscarOFallar(personId);

        email.setIdCorreoPersona(UUID.randomUUID());
        email.setEstadoActivo(true);
        persona.addEmail(email);

        return personPersistencePort.save(persona);
    }

    @Override
    @Transactional
    public Person updateEmail(UUID personId, UUID emailId, EmailPerson email) {
        Person persona = buscarOFallar(personId);

        persona.getEmailPersonList().stream()
                .filter(e -> e.getIdCorreoPersona().equals(emailId))
                .findFirst()
                .ifPresent(e -> e.setCorreoPersona(email.getCorreoPersona()));

        return personPersistencePort.save(persona);
    }

    @Override
    @Transactional
    public Person removeEmail(UUID personId, UUID emailId) {
        Person persona = buscarOFallar(personId);
        persona.removeEmail(emailId);

        return personPersistencePort.save(persona);
    }

    @Override
    @Transactional
    public Person addPhone(UUID personId, PhonePerson phone) {
        Person persona = buscarOFallar(personId);

        phone.setIdTelefonoPersona(UUID.randomUUID());
        phone.setEstadoActivo(true);
        persona.addPhone(phone);

        return personPersistencePort.save(persona);
    }

    @Override
    @Transactional
    public Person updatePhone(UUID personId, UUID phoneId, PhonePerson phone) {
        Person persona = buscarOFallar(personId);

        persona.getPhonePersonList().stream()
                .filter(p -> p.getIdTelefonoPersona().equals(phoneId))
                .findFirst()
                .ifPresent(p -> p.setTelefonoPersona(phone.getTelefonoPersona()));

        return personPersistencePort.save(persona);
    }

    @Override
    @Transactional
    public Person removePhone(UUID personId, UUID phoneId) {
        Person persona = buscarOFallar(personId);
        persona.removePhone(phoneId);

        return personPersistencePort.save(persona);
    }

    private Person buscarOFallar(UUID id) {
        return personPersistencePort.findById(id).orElseThrow(() -> new PersonNotFoundException(id));
    }

    private Person personFrom(PersonUseCaseRequest request, PersonType tipoPersona, UUID identificador) {
        return Person.builder()
                .identificador(identificador)
                .cedula(request.cedula())
                .primerNombre(request.primerNombre())
                .segundoNombre(request.segundoNombre())
                .primerApellido(request.primerApellido())
                .segundoApellido(request.segundoApellido())
                .tipoPersona(tipoPersona)
                // El original recibia este dato en la peticion y nunca lo trasladaba a la persona.
                .segundoTipoPersona(request.segundoTipoPersona())
                .estadoActivo(true)
                .emailPersonList(request.emailPersonList().stream()
                        .map(correo -> EmailPerson.builder()
                                .idCorreoPersona(UUID.randomUUID())
                                .correoPersona(correo.correoPersona())
                                .estadoActivo(true)
                                .build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .phonePersonList(request.phonePersonList().stream()
                        .map(telefono -> PhonePerson.builder()
                                .idTelefonoPersona(UUID.randomUUID())
                                .telefonoPersona(telefono.telefonoPersona())
                                .estadoActivo(true)
                                .build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }

    private PersonIdentityRequest identityRequestFrom(PersonUseCaseRequest request) {
        return PersonIdentityRequest.builder()
                .userName(request.nombreUsuario())
                .email(request.emailPersonList().stream()
                        .findFirst()
                        .map(EmailPersonUseCaseRequest::correoPersona)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Se requiere al menos un correo para crear el usuario")))
                .firstName(request.primerNombre())
                .lastName(request.primerApellido())
                .password(request.password())
                .build();
    }
}
