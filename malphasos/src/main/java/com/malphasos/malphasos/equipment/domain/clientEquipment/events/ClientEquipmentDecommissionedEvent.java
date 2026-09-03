package com.malphasos.malphasos.equipment.domain.clientEquipment.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una unidad salió de servicio. */
public record ClientEquipmentDecommissionedEvent(EventMetadata metadata, ClientEquipmentPayload payload) implements DomainEvent<ClientEquipmentPayload> {

    public static final String TYPE = "client-equipment.decommissioned";
}
