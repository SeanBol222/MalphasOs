package com.malphasos.malphasos.client.application.ports.output;

import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/** Lo que la capa de aplicación necesita de un almacén de áreas de servicio. */
public interface ServiceAreaPersistencePort {

    List<ServiceArea> findAll();

    Optional<ServiceArea> findById(UUID id);

    List<ServiceArea> findByHeadquarter(UUID idSede);

    /**
     * Identificadores de las áreas de estos clientes, cruzando por la sede.
     *
     * <p>Devuelve identificadores y no áreas porque quien pregunta —el filtrado por dueño— solo
     * necesita saber cuáles entran, no qué tienen dentro.
     */
    Set<UUID> findIdsByClients(Collection<UUID> idsClientes);

    ServiceArea save(ServiceArea serviceArea);
}
