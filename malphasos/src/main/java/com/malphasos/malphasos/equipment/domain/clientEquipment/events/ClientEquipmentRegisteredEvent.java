package com.malphasos.malphasos.equipment.domain.clientEquipment.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un cliente incorporó una unidad a su inventario. */
public record ClientEquipmentRegisteredEvent(EventMetadata metadata, ClientEquipmentPayload payload) implements DomainEvent<ClientEquipmentPayload> {

    public static final String TYPE = "client-equipment.registered";
}
