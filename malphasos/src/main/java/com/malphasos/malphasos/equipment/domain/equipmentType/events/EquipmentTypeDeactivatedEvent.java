package com.malphasos.malphasos.equipment.domain.equipmentType.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un tipo de equipo dejó de estar disponible. */
public record EquipmentTypeDeactivatedEvent(EventMetadata metadata, EquipmentTypePayload payload)
        implements DomainEvent<EquipmentTypePayload> {

    public static final String TYPE = "equipment-type.deactivated";
}
