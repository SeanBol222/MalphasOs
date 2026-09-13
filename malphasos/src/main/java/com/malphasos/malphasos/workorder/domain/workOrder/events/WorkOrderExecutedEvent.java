package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** El trabajo termino. Es el hecho del que colgaran los reportes de servicio. */
public record WorkOrderExecutedEvent(EventMetadata metadata, WorkOrderPayload payload) implements DomainEvent<WorkOrderPayload> {

    public static final String TYPE = "work-order.executed";
}
