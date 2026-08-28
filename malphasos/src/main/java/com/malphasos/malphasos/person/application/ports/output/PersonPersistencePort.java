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

    /**
     * Guarda la persona y devuelve el resultado.
     *
     * <p>No existe una operación de borrado: el sistema nunca elimina filas. Desactivar una persona
     * es guardarla con su estado en falso. En el proyecto original el puerto declaraba un
     * {@code delete} cuya implementación se limitaba a llamar a {@code save}, de modo que el nombre
     * describía algo que no ocurría.
     */
    Person save(Person person);
}
