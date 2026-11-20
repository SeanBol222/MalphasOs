package com.malphasos.malphasos.workorder.domain.workOrderStandards.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import java.util.UUID;

/** Los patrones de un tipo en una orden, en el orden en que llegaron. */
public record WorkOrderStandardsAssignedPayload(UUID idOrdenTrabajo, UUID idTipoEquipo, List<UUID> patrones)
        implements Payload {

    public WorkOrderStandardsAssignedPayload {
        patrones = List.copyOf(patrones);
    }
}
