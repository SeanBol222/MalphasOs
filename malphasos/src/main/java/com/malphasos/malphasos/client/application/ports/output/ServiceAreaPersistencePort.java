package com.malphasos.malphasos.client.application.ports.output;

import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lo que la capa de aplicación necesita de un almacén de áreas de servicio. */
public interface ServiceAreaPersistencePort {

    List<ServiceArea> findAll();

    Optional<ServiceArea> findById(UUID id);

    List<ServiceArea> findByHeadquarter(UUID idSede);

    ServiceArea save(ServiceArea serviceArea);
}
