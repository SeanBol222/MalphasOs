package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** El ingeniero empezo a trabajar sobre los equipos de la orden. */
public record WorkOrderStartedEvent(EventMetadata metadata, WorkOrderPayload payload) implements DomainEvent<WorkOrderPayload> {

    public static final String TYPE = "work-order.started";
}
