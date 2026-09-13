package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un equipo entra en el alcance de la orden. */
public record WorkOrderEquipmentAddedEvent(EventMetadata metadata, WorkOrderEquipmentPayload payload) implements DomainEvent<WorkOrderEquipmentPayload> {

    public static final String TYPE = "work-order.equipment-added";
}
