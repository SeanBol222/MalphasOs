package com.malphasos.malphasos.equipment.domain.equipment.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró que una marca fabrica un tipo de equipo. */
public record EquipmentCreatedEvent(EventMetadata metadata, EquipmentPayload payload) implements DomainEvent<EquipmentPayload> {

    public static final String TYPE = "equipment.created";
}
