package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** La orden pasa a manos de un ingeniero. Asignar es un hecho aparte de crear, y por eso el realm le da autoridad propia. */
public record WorkOrderAssignedEvent(EventMetadata metadata, WorkOrderPayload payload) implements DomainEvent<WorkOrderPayload> {

    public static final String TYPE = "work-order.assigned";
}
