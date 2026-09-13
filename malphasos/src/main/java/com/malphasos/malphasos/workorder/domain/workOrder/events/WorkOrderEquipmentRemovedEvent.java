package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un equipo sale del alcance de la orden antes de que se ejecute. */
public record WorkOrderEquipmentRemovedEvent(EventMetadata metadata, WorkOrderEquipmentPayload payload) implements DomainEvent<WorkOrderEquipmentPayload> {

    public static final String TYPE = "work-order.equipment-removed";
}
