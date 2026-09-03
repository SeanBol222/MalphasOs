package com.malphasos.malphasos.equipment.domain.equipmentType.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró un tipo de equipo nuevo. */
public record EquipmentTypeCreatedEvent(EventMetadata metadata, EquipmentTypePayload payload)
        implements DomainEvent<EquipmentTypePayload> {

    public static final String TYPE = "equipment-type.created";
}
