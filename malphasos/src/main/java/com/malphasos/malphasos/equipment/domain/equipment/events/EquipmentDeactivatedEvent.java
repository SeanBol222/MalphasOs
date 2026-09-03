package com.malphasos.malphasos.equipment.domain.equipment.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una marca dejó de fabricar un tipo de equipo. */
public record EquipmentDeactivatedEvent(EventMetadata metadata, EquipmentPayload payload) implements DomainEvent<EquipmentPayload> {

    public static final String TYPE = "equipment.deactivated";
}
