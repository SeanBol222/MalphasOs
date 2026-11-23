package com.malphasos.malphasos.equipment.application.services.externalIntervention;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ExternalInterventionServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ExternalInterventionPersistencePort;
import com.malphasos.malphasos.equipment.application.services.externalIntervention.commands.RecordExternalInterventionCommand;
import com.malphasos.malphasos.equipment.domain.exception.ExternalInterventionNotFoundException;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que se le hizo a un equipo fuera de MalphasOS (V61, 2026-11-23).
 *
 * <p><b>El alcance decide sobre qué equipos se escribe</b>: lo anotan ingenieros y administradores, y también
 * el supervisor del cliente —decidido por el usuario: la calibración de otra empresa la contrata a veces el
 * cliente—, pero solo en los equipos de los clientes que supervisa. Se comprueba como en el historial,
 * preguntando por el equipo con ese alcance: si es de otro cliente, «no existe».
 */
@Service
public class ExternalInterventionService implements ExternalInterventionServicePort {

    private final ExternalInterventionPersistencePort externalInterventionPersistencePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final EventDispatcherPort eventDispatcherPort;
    private final Clock reloj;

    @Autowired
    public ExternalInterventionService(
            ExternalInterventionPersistencePort externalInterventionPersistencePort,
            ClientEquipmentServicePort clientEquipmentServicePort,
            EventDispatcherPort eventDispatcherPort,
            @Value("${app.time-zone:America/Bogota}") String zona) {
        this(externalInterventionPersistencePort, clientEquipmentServicePort, eventDispatcherPort,
                Clock.system(ZoneId.of(zona)));
    }

    public ExternalInterventionService(
            ExternalInterventionPersistencePort externalInterventionPersistencePort,
            ClientEquipmentServicePort clientEquipmentServicePort,
            EventDispatcherPort eventDispatcherPort,
            Clock reloj) {
        this.externalInterventionPersistencePort = externalInterventionPersistencePort;
        this.clientEquipmentServicePort = clientEquipmentServicePort;
        this.eventDispatcherPort = eventDispatcherPort;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalIntervention> findByEquipment(UUID idEquipoCliente, ReadScope alcance) {
        clientEquipmentServicePort.findById(idEquipoCliente, alcance);

        return externalInterventionPersistencePort.findActiveByEquipment(idEquipoCliente);
    }

    @Override
    @Transactional
    public ExternalIntervention record(RecordExternalInterventionCommand command, ReadScope alcance) {
        clientEquipmentServicePort.findById(command.idEquipoCliente(), alcance);

        ExternalIntervention intervencion = ExternalIntervention.create(command.idEquipoCliente(),
                command.realizada(), command.tipoServicio(), command.descripcion(), command.empresa(),
                command.resultado(), command.documento(), LocalDate.now(reloj));
        ExternalIntervention guardada = externalInterventionPersistencePort.save(intervencion);
        eventDispatcherPort.dispatchAll(intervencion.pullEvents());

        return guardada;
    }

    @Override
    @Transactional
    public void deactivate(UUID idEquipoCliente, UUID idIntervencion, ReadScope alcance) {
        clientEquipmentServicePort.findById(idEquipoCliente, alcance);
        ExternalIntervention intervencion = externalInterventionPersistencePort.findById(idIntervencion)
                .filter(encontrada -> encontrada.getIdEquipoCliente().equals(idEquipoCliente))
                .orElseThrow(() -> new ExternalInterventionNotFoundException(idIntervencion));

        intervencion.deactivate();
        externalInterventionPersistencePort.save(intervencion);
        eventDispatcherPort.dispatchAll(intervencion.pullEvents());
    }
}
