package com.malphasos.malphasos.equipment.domain.equipmentType.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron las características de un tipo de equipo. */
public record EquipmentTypeUpdatedEvent(EventMetadata metadata, EquipmentTypePayload payload)
        implements DomainEvent<EquipmentTypePayload> {

    public static final String TYPE = "equipment-type.updated";
}
