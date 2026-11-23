package com.malphasos.malphasos.person.application.ports.input;

import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso disponibles sobre personas.
 *
 * <p>Es el contrato que consumen los adaptadores de entrada. Los tres métodos {@code register*} dan
 * de alta además un usuario en el proveedor de identidad: desde V53 toda persona accede al sistema, y
 * el alta sin cuenta —que era para los encargados— salió con ellos.
 *
 * <p>Todas las operaciones que reciben un identificador lanzan {@link PersonNotFoundException} si
 * no corresponde a ninguna persona.
 */
public interface PersonServicePort {

    Person findById(UUID id);

    List<Person> findAll();

    /** Registra un ingeniero junto con su usuario en el proveedor de identidad. */
    Person registerEngineer(PersonUseCaseRequest request);

    /** Registra un administrador junto con su usuario en el proveedor de identidad. */
    Person registerAdmin(PersonUseCaseRequest request);

    /** Registra un representante legal de cliente junto con su usuario en el proveedor de identidad. */
    Person registerSupervisor(PersonUseCaseRequest request);

    Person update(UUID id, Person person);

    /** Desactiva la persona sin borrarla, conservando su historial. */
    void delete(UUID id);

    /** Vuelve a activar a una persona retirada, y a su cuenta si la tiene. */
    Person reactivate(UUID id);

    Person addEmail(UUID personId, EmailPerson email);

    Person addPhone(UUID personId, PhonePerson phone);

    Person updateEmail(UUID personId, UUID emailId, EmailPerson email);

    Person updatePhone(UUID personId, UUID phoneId, PhonePerson phone);

    /** Desactiva el correo sin borrarlo. */
    Person removeEmail(UUID personId, UUID emailId);

    /** Hace de ese correo el de la cuenta y lo lleva a Keycloak (V40). */
    Person markPrimaryEmail(UUID personId, UUID emailId);

    /** Pone una contraseña temporal en la cuenta de la persona, que tendrá que cambiar al entrar. */
    void resetPassword(UUID personId, String temporaryPassword);

    /** El nombre de usuario de su cuenta, o vacío si no la tiene. */
    Optional<String> accountOf(UUID personId);

    /**
     * Da cuenta a una ficha que no la tiene (2026-11-02), con una contraseña temporal. Su identificador
     * pasa a ser el del usuario nuevo, que es el que Keycloak elige.
     *
     * @return la persona con su identificador nuevo
     */
    Person grantAccess(UUID personId, String userName, String temporaryPassword);

    /** Desactiva el teléfono sin borrarlo. */
    Person removePhone(UUID personId, UUID phoneId);
}
