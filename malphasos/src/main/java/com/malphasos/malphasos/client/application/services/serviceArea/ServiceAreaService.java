package com.malphasos.malphasos.client.application.services.serviceArea;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.DeactivateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.RenameServiceAreaCommand;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.shared.application.model.ReadScope;
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
    public List<ServiceArea> findByHeadquarter(UUID idSede, ReadScope alcance) {
        requireHeadquarterInScope(idSede, alcance);

        return serviceAreaPersistencePort.findByHeadquarter(idSede);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceArea findById(UUID id, ReadScope alcance) {
        ServiceArea area = serviceAreaPersistencePort.findById(id)
                .orElseThrow(() -> new ServiceAreaNotFoundException(id));

        // El dueño de un área está a dos saltos —area -> sede -> cliente—, de modo que comprobarlo
        // cuesta una consulta más. Solo se paga cuando el alcance restringe: a la gente de la casa
        // no se le cobra el filtro que no se le aplica.
        if (!alcance.coversEverything() && !alcance.covers(requireHeadquarter(area.getIdSede()).getIdCliente())) {
            throw new ServiceAreaNotFoundException(id);
        }

        return area;
    }

    /**
     * Resuelve aquí, en un solo salto para quien pregunta, el cliente dueño de un área.
     *
     * <p>El dato ya está: el área guarda su sede y la sede guarda su cliente. Lo que este método
     * aporta es que ese camino se recorra dentro de este módulo y no en el que consulta.
     */
    @Override
    @Transactional(readOnly = true)
    public UUID findOwningClient(UUID idAreaServicio) {
        // Sin restricción a propósito: esta operación la consulta otro módulo para decidir si un
        // traslado cruza de cliente, y esa regla no depende de quién esté mirando.
        ServiceArea area = findById(idAreaServicio, ReadScope.unrestricted());

        return requireHeadquarter(area.getIdSede()).getIdCliente();
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
        // Igual que en las demás escrituras del módulo: sin restricción, porque renombrar exige
        // service-area.write y el alcance solo acota lecturas.
        ServiceArea area = findById(command.id(), ReadScope.unrestricted());
        area.rename(command.nombre());

        return persistAndPublish(area);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateServiceAreaCommand command) {
        ServiceArea area = findById(command.id(), ReadScope.unrestricted());
        area.deactivate();

        persistAndPublish(area);
    }

    /** La sede existe y su cliente entra en el alcance, o no existe para quien pregunta. */
    private void requireHeadquarterInScope(UUID idSede, ReadScope alcance) {
        Headquarter sede = requireHeadquarter(idSede);

        if (!alcance.covers(sede.getIdCliente())) {
            throw new HeadquarterNotFoundException(idSede);
        }
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
