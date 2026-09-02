package com.malphasos.malphasos.client.application.ports.output;

import com.malphasos.malphasos.client.domain.manager.Manager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la capa de aplicación necesita de un almacén de encargados.
 *
 * <p>Se busca por el identificador de la persona porque es también el del encargado: no hay
 * identidad propia que buscar.
 */
public interface ManagerPersistencePort {

    List<Manager> findAll();

    Optional<Manager> findByPerson(UUID idPersona);

    List<Manager> findByHeadquarter(UUID idSede);

    List<Manager> findByServiceArea(UUID idArea);

    Manager save(Manager manager);
}
