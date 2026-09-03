package com.malphasos.malphasos.equipment.domain.manufacturer.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró un fabricante nuevo. */
public record ManufacturerCreatedEvent(EventMetadata metadata, ManufacturerPayload payload)
        implements DomainEvent<ManufacturerPayload> {

    public static final String TYPE = "manufacturer.created";
}
