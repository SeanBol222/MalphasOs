package com.malphasos.malphasos.person.application.services.person;

import com.malphasos.malphasos.person.application.model.identity.PersonIdentityProfile;
import com.malphasos.malphasos.person.application.model.identity.PersonIdentityRequest;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserNotFoundException;
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
    public Person registerEngineer(PersonUseCaseRequest request) {
        return register(request, PersonType.ENGINEER, RoleType.ENGINEER);
    }

    @Override
    public Person registerAdmin(PersonUseCaseRequest request) {
        return register(request, PersonType.ADMIN, RoleType.ADMIN);
    }

    @Override
    public Person registerSupervisor(PersonUseCaseRequest request) {
        return register(request, PersonType.SUPERVISOR, RoleType.SUPERVISOR);
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

        String userId = personIdentityPort.createUser(identityRequestFrom(request), roleType);

        try {
            Person person = personFrom(request, tipoPersona, UUID.fromString(userId));

            return personPersistencePort.save(person);

        } catch (RuntimeException failure) {
            rollbackUser(userId, failure);
            throw failure;
        }
    }

    /**
     * Elimina el usuario creado cuando el resto del alta falló.
     *
     * <p>Si la eliminación también falla, se adjunta como excepción suprimida en lugar de
     * reemplazar al error original: quien depure necesita ver la causa real del fallo, y además
     * saber que quedó un usuario huérfano.
     */
    private void rollbackUser(String userId, RuntimeException failure) {
        try {
            personIdentityPort.deleteUser(userId);
        } catch (RuntimeException rollbackFailure) {
            log.error(
                    "No se pudo eliminar el usuario {} tras fallar el registro. Queda huerfano en el "
                            + "proveedor de identidad.",
                    userId,
                    rollbackFailure);
            failure.addSuppressed(rollbackFailure);
        }
    }

    /**
     * Actualiza los datos de una persona y los propaga al proveedor de identidad.
     *
     * <p>Solo viajan el nombre y el apellido, que son los mismos campos con los que se creó el
     * usuario. El nombre de usuario no se toca porque esta operación ni siquiera lo recibe; el
     * correo se administra en su propio recurso y la persona puede tener varios sin que ninguno esté
     * marcado como principal; y la contraseña no cambia al editar un apellido. El tipo de persona
     * tampoco se propaga: cambiar de rol es mover al usuario de grupo en Keycloak, una operación con
     * consecuencias de permisos que merece su propio caso de uso.
     *
     * <p>El orden importa. Primero se validan los datos, después se escribe en Keycloak y solo al
     * final en la base: si Keycloak falla, la excepción deshace la transacción y ambos sistemas
     * quedan como estaban. La ventana que queda es estrecha —que Keycloak acepte el cambio y la
     * transacción falle al confirmar—, y su consecuencia es que los nombres difieran hasta la
     * siguiente edición, sin efecto sobre quién puede entrar.
     */
    @Override
    @Transactional
    public Person update(UUID id, Person person) {
        return personPersistencePort.findById(id)
                .map(existing -> {
                    PersonType tipoAnterior = existing.getTipoPersona();

                    existing.setCedula(person.getCedula());
                    existing.setPrimerNombre(person.getPrimerNombre());
                    existing.setSegundoNombre(person.getSegundoNombre());
                    existing.setPrimerApellido(person.getPrimerApellido());
                    existing.setSegundoApellido(person.getSegundoApellido());
                    existing.setTipoPersona(person.getTipoPersona());

                    propagateProfile(existing);
                    propagateGroup(existing, tipoAnterior);

                    return personPersistencePort.save(existing);
                })
                .orElseThrow(() -> new PersonNotFoundException(id));
    }

    /**
     * Mueve la cuenta al grupo que le toca cuando el tipo de la persona ha cambiado.
     *
     * <p><b>Por qué esto no era opcional.</b> Hasta el 2026-10-04 cambiar el tipo no tocaba la
     * identidad, de modo que quien dejaba de ser ingeniero conservaba sus permisos. Y desde que hay
     * filtrado por dueño pasa también lo contrario: el alcance de lectura se decide por el tipo, así
     * que a un supervisor al que se le cambie el tipo se le quita el filtro <b>sin quitarle su
     * grupo</b>, y pasa a leer todo lo que sus autoridades alcanzan. La fila y la identidad decían
     * cosas distintas y el sistema creía las dos a la vez.
     *
     * <p>Solo se llama si el tipo cambió: editar un apellido no tiene por qué costar dos llamadas a
     * Keycloak. La idempotencia está además en el adaptador, de modo que esta comprobación es por
     * coste y no por corrección.
     *
     * <p>Como en {@link #propagateProfile}, que la persona no tenga cuenta no se trata como error: hasta
     * V53 era el caso normal de un encargado, y las fichas de entonces siguen sin usuario.
     */
    private void propagateGroup(Person person, PersonType tipoAnterior) {
        if (person.getTipoPersona() == tipoAnterior) {
            return;
        }

        try {
            personIdentityPort.syncGroup(person.getIdentificador().toString(), groupRoleFor(person.getTipoPersona()));

        } catch (KeycloakUserNotFoundException sinCuenta) {
            log.debug(
                    "La persona {} no tiene usuario en el proveedor de identidad; no hay grupo que "
                            + "sincronizar.",
                    person.getIdentificador());
        }
    }

    /**
     * Con qué permisos accede alguien de este tipo.
     *
     * <p>Es la traducción entre los cuatro valores de {@link PersonType} y los tres grupos que el
     * realm reparte, y vive aquí —en la capa de aplicación— porque es una decisión de negocio y no
     * del proveedor de identidad. Se resuelve con una expresión {@code switch} para que añadir un
     * tipo nuevo obligue a decidir con qué entra: la misma precaución que toman {@code groupFor} y
     * {@code requiredAuthority}, y por la misma razón.
     *
     * <p><b>{@code SUPER_ADMIN} entra como administrador</b>, y no es una coincidencia de nombres:
     * <b>ningún grupo del realm concede {@code super.admin.full}</b>, a propósito, porque el escalón de
     * arriba se otorga a mano. Dejarlo sin grupo dejaría sin acceso a quien se acaba de promover; se le
     * da el grupo más alto que un grupo puede dar y se registra que falta el resto.
     *
     * <p>Hasta V53 había un tipo que no accedía —el encargado— y devolvía {@code null}. Desde entonces
     * toda persona tiene cuenta.
     */
    private RoleType groupRoleFor(PersonType tipo) {
        return switch (tipo) {
            case ENGINEER -> RoleType.ENGINEER;
            case SUPERVISOR -> RoleType.SUPERVISOR;
            case ADMIN -> RoleType.ADMIN;
            case SUPER_ADMIN -> {
                log.warn(
                        "Se ha dejado a un SUPER_ADMIN en el grupo de administradores: ningun grupo "
                                + "del realm concede super.admin.full, y ese escalon hay que "
                                + "otorgarlo a mano en Keycloak.");
                yield RoleType.ADMIN;
            }
        };
    }

    /**
     * Da de baja a una persona: le retira el acceso al sistema y la marca como inactiva.
     *
     * <p>El borrado lógico por sí solo dejaba una puerta abierta. El dato decía "inactiva" y la
     * identidad seguía diciendo "pase": la cuenta de Keycloak sobrevivía intacta y continuaba
     * emitiendo tokens válidos.
     *
     * <p>Se deshabilita el usuario en lugar de eliminarlo, por simetría con el borrado lógico que el
     * resto del sistema usa: la cuenta y su historial se conservan, y volver a darle entrada a la
     * persona es deshacer una bandera y no recrear una identidad.
     *
     * <p>Primero se retira el acceso y después se guarda, y no al revés, porque los dos sistemas no
     * comparten transacción y hay que elegir en qué dirección puede quedar la inconsistencia. Con
     * este orden, si la persistencia falla queda una persona activa que no puede entrar: molesto,
     * visible y reparable repitiendo la baja. Con el orden contrario quedaría una persona dada de
     * baja que sí puede entrar, que es exactamente el defecto que este método corrige.
     *
     * <p>No se comprueba si la persona ya estaba inactiva antes de llamar a Keycloak: repetir la
     * baja es barato, no tiene efecto sobre una cuenta ya deshabilitada y repara la desincronización
     * si alguien reactivó la cuenta a mano desde la consola de administración.
     *
     * <p>Queda una ventana que esto no cierra. Deshabilitar impide autenticarse de nuevo y renovar
     * el token —comprobado contra Keycloak 26.6.1: el intento responde {@code invalid_grant}—, pero
     * un token de acceso ya emitido sigue siendo válido hasta que caduca, porque este servicio lo
     * valida sin consultar a Keycloak, solo con la firma. Con el realm de desarrollo esa ventana es
     * de cinco minutos, que es la vida del token de acceso.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        Person person = personPersistencePort.findById(id)
                .orElseThrow(() -> new PersonNotFoundException(id));

        revokeAccess(person);

        person.setEstadoActivo(false);
        personPersistencePort.save(person);
    }

    /**
     * Retira el acceso de la persona al sistema, si es que lo tenía.
     *
     * <p>Que no exista el usuario no impide la baja: quien se registró con {@link #save} nunca tuvo
     * cuenta, y una cuenta pudo borrarse a mano. En ambos casos el objetivo —que nadie entre con esa
     * identidad— ya se cumple. Se registra en el log porque, si la persona sí debía tener cuenta, es
     * la única señal de que los dos sistemas estaban desincronizados.
     *
     * <p>Cualquier otro fallo de Keycloak sí interrumpe la baja: no se puede dar por retirada a una
     * persona cuyo acceso no se ha podido comprobar que quedó cerrado.
     */
    private void revokeAccess(Person person) {
        try {
            personIdentityPort.disableUser(person.getIdentificador().toString());

        } catch (KeycloakUserNotFoundException sinCuenta) {
            log.info(
                    "La persona {} no tiene usuario en el proveedor de identidad; se da de baja solo "
                            + "en la base de datos.",
                    person.getIdentificador());
        }
    }

    /**
     * Lleva al proveedor de identidad los datos personales que allí también se guardan.
     *
     * <p>Como en {@link #revokeAccess}, que la persona no tenga cuenta no es un error: es el caso
     * normal de quien se registró con {@link #save}. Aquí ni siquiera se registra como advertencia,
     * porque no hay nada que sincronizar.
     */
    private void propagateProfile(Person person) {
        try {
            personIdentityPort.updateUserProfile(
                    person.getIdentificador().toString(),
                    PersonIdentityProfile.builder()
                            .firstName(person.getPrimerNombre())
                            .lastName(person.getPrimerApellido())
                            .build());

        } catch (KeycloakUserNotFoundException sinCuenta) {
            log.debug(
                    "La persona {} no tiene usuario en el proveedor de identidad; no hay perfil que "
                            + "actualizar.",
                    person.getIdentificador());
        }
    }

    @Override
    @Transactional
    public Person addEmail(UUID personId, EmailPerson email) {
        Person person = findOrFail(personId);

        email.setIdCorreoPersona(UUID.randomUUID());
        email.setEstadoActivo(true);
        person.addEmail(email);

        return personPersistencePort.save(person);
    }

    /**
     * Corrige un correo. Si es el de la cuenta, la corrección llega también a Keycloak, y antes de
     * guardar: si Keycloak la rechaza —otra cuenta ya lo usa— no se guarda nada y los dos sistemas
     * siguen de acuerdo.
     */
    @Override
    @Transactional
    public Person updateEmail(UUID personId, UUID emailId, EmailPerson email) {
        Person person = findOrFail(personId);

        person.getEmailPersonList().stream()
                .filter(e -> e.getIdCorreoPersona().equals(emailId))
                .findFirst()
                .ifPresent(e -> {
                    boolean cambia = !email.getCorreoPersona().equals(e.getCorreoPersona());
                    e.setCorreoPersona(email.getCorreoPersona());
                    if (e.isPrincipal() && e.isEstadoActivo() && cambia) {
                        propagateEmail(person, email.getCorreoPersona());
                    }
                });

        return personPersistencePort.save(person);
    }

    /**
     * Cambia el correo de la cuenta (V40, 2026-10-17). En el mismo orden que la edición: primero la
     * regla, después Keycloak, al final la base.
     *
     * <p>Una persona cuya cuenta no aparece en Keycloak —borrada a mano— sí se marca: la ficha es la que
     * manda, y el conciliador lo deja dicho en el log.
     */
    @Override
    @Transactional
    public Person markPrimaryEmail(UUID personId, UUID emailId) {
        Person person = findOrFail(personId);

        EmailPerson elegido = person.markPrimaryEmail(emailId);
        propagateEmail(person, elegido.getCorreoPersona());

        return personPersistencePort.save(person);
    }

    /**
     * Restablece la contraseña de una cuenta con una temporal (2026-10-17): es como se resuelve hoy una
     * contraseña olvidada, porque el realm no tiene servidor de correo para el «¿Olvidó su contraseña?».
     *
     * <p>No toca la base: la contraseña no se guarda aquí. Se rechaza a una persona retirada, cuya
     * cuenta está deshabilitada a propósito. Que Keycloak no la encuentre sí es un error, y llega a
     * quien llama.
     */
    @Override
    @Transactional(readOnly = true)
    public void resetPassword(UUID personId, String temporaryPassword) {
        Person person = findOrFail(personId);

        if (!person.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "Esa persona esta retirada y su cuenta deshabilitada: no se le restablece la contrasena");
        }

        personIdentityPort.resetPassword(person.getIdentificador().toString(), temporaryPassword);
    }

    /** Como {@link #propagateProfile}: que la persona no tenga cuenta no es un error. */
    private void propagateEmail(Person person, String correo) {
        try {
            personIdentityPort.updateUserEmail(person.getIdentificador().toString(), correo);

        } catch (KeycloakUserNotFoundException sinCuenta) {
            log.warn(
                    "La persona {} no tiene usuario en el proveedor de identidad; su correo principal "
                            + "solo cambia en la base de datos.",
                    person.getIdentificador());
        }
    }

    @Override
    @Transactional
    public Person removeEmail(UUID personId, UUID emailId) {
        Person person = findOrFail(personId);
        person.removeEmail(emailId);

        return personPersistencePort.save(person);
    }

    @Override
    @Transactional
    public Person addPhone(UUID personId, PhonePerson phone) {
        Person person = findOrFail(personId);

        phone.setIdTelefonoPersona(UUID.randomUUID());
        phone.setEstadoActivo(true);
        person.addPhone(phone);

        return personPersistencePort.save(person);
    }

    @Override
    @Transactional
    public Person updatePhone(UUID personId, UUID phoneId, PhonePerson phone) {
        Person person = findOrFail(personId);

        person.getPhonePersonList().stream()
                .filter(p -> p.getIdTelefonoPersona().equals(phoneId))
                .findFirst()
                .ifPresent(p -> p.setTelefonoPersona(phone.getTelefonoPersona()));

        return personPersistencePort.save(person);
    }

    @Override
    @Transactional
    public Person removePhone(UUID personId, UUID phoneId) {
        Person person = findOrFail(personId);
        person.removePhone(phoneId);

        return personPersistencePort.save(person);
    }

    private Person findOrFail(UUID id) {
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
                .estadoActivo(true)
                .emailPersonList(correosDeLaCuenta(request))
                .phonePersonList(listaOVacia(request.phonePersonList()).stream()
                        .map(phone -> PhonePerson.builder()
                                .idTelefonoPersona(UUID.randomUUID())
                                .telefonoPersona(phone.telefonoPersona())
                                .estadoActivo(true)
                                .build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }

    /**
     * Los correos de un alta con cuenta, con el primero marcado como el de la cuenta (V40): es el
     * mismo que {@link #identityRequestFrom} manda a Keycloak, así que los dos sistemas empiezan de
     * acuerdo.
     */
    private List<EmailPerson> correosDeLaCuenta(PersonUseCaseRequest request) {
        List<EmailPerson> correos = listaOVacia(request.emailPersonList()).stream()
                .map(email -> EmailPerson.builder()
                        .idCorreoPersona(UUID.randomUUID())
                        .correoPersona(email.correoPersona())
                        .estadoActivo(true)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        if (!correos.isEmpty()) {
            correos.get(0).setPrincipal(true);
        }

        return correos;
    }

    /**
     * Una lista que la petición puede no traer. Correos y teléfonos son opcionales en el contrato
     * —salvo el correo de quien tiene cuenta, que se exige aparte y con su propio mensaje—, y que no
     * vengan significa que no hay ninguno.
     */
    private static <T> List<T> listaOVacia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    private PersonIdentityRequest identityRequestFrom(PersonUseCaseRequest request) {
        return PersonIdentityRequest.builder()
                .userName(request.nombreUsuario())
                .email(listaOVacia(request.emailPersonList()).stream()
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
