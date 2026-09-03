package com.malphasos.malphasos.equipment.domain.manufacturer.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un fabricante dejó de estar disponible. */
public record ManufacturerDeactivatedEvent(EventMetadata metadata, ManufacturerPayload payload)
        implements DomainEvent<ManufacturerPayload> {

    public static final String TYPE = "manufacturer.deactivated";
}
