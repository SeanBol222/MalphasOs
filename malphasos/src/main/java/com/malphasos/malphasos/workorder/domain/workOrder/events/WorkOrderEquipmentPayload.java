package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** El equipo que entra o sale de una orden, con el área en la que estaba al seleccionarlo. */
public record WorkOrderEquipmentPayload(UUID idEquipoCliente, UUID idAreaServicio)
        implements Payload {
}
