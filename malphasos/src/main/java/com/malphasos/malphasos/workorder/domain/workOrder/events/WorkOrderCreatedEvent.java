package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se programo un mantenimiento para una sede de un cliente. */
public record WorkOrderCreatedEvent(EventMetadata metadata, WorkOrderPayload payload) implements DomainEvent<WorkOrderPayload> {

    public static final String TYPE = "work-order.created";
}
