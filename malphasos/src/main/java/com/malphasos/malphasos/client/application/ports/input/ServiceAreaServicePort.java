package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.DeactivateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.RenameServiceAreaCommand;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre áreas de servicio. */
public interface ServiceAreaServicePort {

    List<ServiceArea> findAll();

    /** Áreas de una sede. Falla si la sede no existe. */
    List<ServiceArea> findByHeadquarter(UUID idSede);

    ServiceArea findById(UUID id);

    ServiceArea create(CreateServiceAreaCommand command);

    ServiceArea rename(RenameServiceAreaCommand command);

    /** Cierra el área sin borrarla. */
    void deactivate(DeactivateServiceAreaCommand command);
}
