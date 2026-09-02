package com.malphasos.malphasos.client.application.services.serviceArea;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.DeactivateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.RenameServiceAreaCommand;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de áreas de servicio.
 *
 * <p>Un área no se abre en una sede cerrada: sería registrar actividad en un sitio que ya no opera.
 * El esquema no puede expresar esa regla —una clave foránea comprueba que la sede exista, no que
 * esté activa—, así que vive aquí.
 */
@Service
@RequiredArgsConstructor
public class ServiceAreaService implements ServiceAreaServicePort {

    private final ServiceAreaPersistencePort serviceAreaPersistencePort;
    private final HeadquarterPersistencePort headquarterPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceArea> findAll() {
        return serviceAreaPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceArea> findByHeadquarter(UUID idSede) {
        requireHeadquarter(idSede);

        return serviceAreaPersistencePort.findByHeadquarter(idSede);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceArea findById(UUID id) {
        return serviceAreaPersistencePort.findById(id)
                .orElseThrow(() -> new ServiceAreaNotFoundException(id));
    }

    @Override
    @Transactional
    public ServiceArea create(CreateServiceAreaCommand command) {
        Headquarter sede = requireHeadquarter(command.idSede());

        if (!sede.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se puede abrir un area en una sede cerrada: " + command.idSede());
        }

        return persistAndPublish(ServiceArea.create(command.nombre(), command.idSede()));
    }

    @Override
    @Transactional
    public ServiceArea rename(RenameServiceAreaCommand command) {
        ServiceArea area = findById(command.id());
        area.rename(command.nombre());

        return persistAndPublish(area);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateServiceAreaCommand command) {
        ServiceArea area = findById(command.id());
        area.deactivate();

        persistAndPublish(area);
    }

    private Headquarter requireHeadquarter(UUID idSede) {
        if (idSede == null) {
            throw new HeadquarterNotFoundException(null);
        }

        return headquarterPersistencePort.findById(idSede)
                .orElseThrow(() -> new HeadquarterNotFoundException(idSede));
    }

    private ServiceArea persistAndPublish(ServiceArea area) {
        ServiceArea guardada = serviceAreaPersistencePort.save(area);
        eventDispatcherPort.dispatchAll(area.pullEvents());

        return guardada;
    }
}
