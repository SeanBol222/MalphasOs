package com.malphasos.malphasos.equipment.domain.clientEquipment.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una unidad se traslado a otra área de servicio. */
public record ClientEquipmentRelocatedEvent(EventMetadata metadata, ClientEquipmentPayload payload) implements DomainEvent<ClientEquipmentPayload> {

    public static final String TYPE = "client-equipment.relocated";
}
