package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** La orden se retira sin borrarse. No es lo mismo que ejecutarla: es cancelarla. */
public record WorkOrderDeactivatedEvent(EventMetadata metadata, WorkOrderPayload payload) implements DomainEvent<WorkOrderPayload> {

    public static final String TYPE = "work-order.deactivated";
}
