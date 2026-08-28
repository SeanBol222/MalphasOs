package com.malphasos.malphasos.person.application.ports.input;

import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso disponibles sobre personas.
 *
 * <p>Es el contrato que consumen los adaptadores de entrada. Los tres métodos {@code register*}
 * se distinguen del resto en que además dan de alta un usuario en el proveedor de identidad: son
 * altas de personas que acceden al sistema, mientras que {@link #save} registra a alguien que solo
 * existe como dato, sin credenciales.
 *
 * <p>Todas las operaciones que reciben un identificador lanzan {@link PersonNotFoundException} si
 * no corresponde a ninguna persona.
 */
public interface PersonServicePort {

    Person findById(UUID id);

    List<Person> findAll();

    /** Registra una persona sin acceso al sistema. */
    Person save(Person person);

    /** Registra un ingeniero junto con su usuario en el proveedor de identidad. */
    Person registerEngineer(PersonUseCaseRequest request);

    /** Registra un administrador junto con su usuario en el proveedor de identidad. */
    Person registerAdmin(PersonUseCaseRequest request);

    /** Registra un representante legal de cliente junto con su usuario en el proveedor de identidad. */
    Person registerCeoClient(PersonUseCaseRequest request);

    Person update(UUID id, Person person);

    /** Desactiva la persona sin borrarla, conservando su historial. */
    void delete(UUID id);

    Person addEmail(UUID personId, EmailPerson email);

    Person addPhone(UUID personId, PhonePerson phone);

    Person updateEmail(UUID personId, UUID emailId, EmailPerson email);

    Person updatePhone(UUID personId, UUID phoneId, PhonePerson phone);

    /** Desactiva el correo sin borrarlo. */
    Person removeEmail(UUID personId, UUID emailId);

    /** Desactiva el teléfono sin borrarlo. */
    Person removePhone(UUID personId, UUID phoneId);
}
