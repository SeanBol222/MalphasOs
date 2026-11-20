package com.malphasos.malphasos.workorder.application.ports.output;

import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import java.util.List;
import java.util.UUID;

/** Almacén de los patrones de cada orden por tipo de equipo (V60). Guardarlos vacíos los quita. */
public interface WorkOrderStandardsPersistencePort {

    /** Los de un tipo en la orden; vacíos si no lleva ninguno. */
    WorkOrderStandards find(UUID idOrdenTrabajo, UUID idTipoEquipo);

    /** Los tipos con algún patrón en la orden. Los que no llevan ninguno no aparecen. */
    List<WorkOrderStandards> findByWorkOrder(UUID idOrdenTrabajo);

    WorkOrderStandards save(WorkOrderStandards patrones);
}
