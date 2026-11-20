package com.malphasos.malphasos.workorder.application.ports.input;

import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands.AssignWorkOrderStandardsCommand;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Los patrones que una orden lleva a su visita, por tipo de equipo (V60). */
public interface WorkOrderStandardsServicePort {

    /** Los de cada tipo con equipos en la orden, vacíos si no lleva ninguno, si la orden cae en el alcance. */
    List<WorkOrderStandards> standardsOf(UUID idOrdenTrabajo, ReadScope alcance);

    /**
     * Los que la orden lleva para el tipo de este equipo: los que su reporte recibe al abrirse. Sin
     * restricción: se pide desde un reporte que ya pasó por su lectura acotada.
     */
    Set<UUID> standardsForEquipment(UUID idOrdenTrabajo, UUID idEquipoCliente);

    WorkOrderStandards assign(AssignWorkOrderStandardsCommand command);
}
