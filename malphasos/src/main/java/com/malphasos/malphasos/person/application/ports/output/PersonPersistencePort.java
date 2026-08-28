package com.malphasos.malphasos.person.application.ports.output;

import com.malphasos.malphasos.person.domain.person.Person;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida hacia el almacenamiento de personas.
 *
 * <p>La capa de aplicación depende de esta interfaz y no de la tecnología concreta que la
 * implementa, de modo que cambiar el motor de persistencia no obliga a tocar la lógica de negocio.
 */
public interface PersonPersistencePort {

    Optional<Person> findById(UUID id);

    List<Person> findAll();

    Person save(Person person);

    void delete(Person person);
}
