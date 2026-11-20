package com.malphasos.malphasos.workorder.domain.workOrderStandards.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los patrones que una orden lleva para un tipo de equipo. */
public record WorkOrderStandardsAssignedEvent(EventMetadata metadata, WorkOrderStandardsAssignedPayload payload)
        implements DomainEvent<WorkOrderStandardsAssignedPayload> {

    public static final String TYPE = "work-order.standards-assigned";
}
