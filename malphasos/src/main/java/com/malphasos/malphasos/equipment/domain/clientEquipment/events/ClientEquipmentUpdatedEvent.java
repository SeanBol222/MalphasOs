package com.malphasos.malphasos.equipment.domain.clientEquipment.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los datos de compra de una unidad. */
public record ClientEquipmentUpdatedEvent(EventMetadata metadata, ClientEquipmentPayload payload) implements DomainEvent<ClientEquipmentPayload> {

    public static final String TYPE = "client-equipment.updated";
}
