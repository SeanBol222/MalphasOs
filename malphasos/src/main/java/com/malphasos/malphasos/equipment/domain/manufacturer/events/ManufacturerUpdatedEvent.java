package com.malphasos.malphasos.equipment.domain.manufacturer.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los datos de un fabricante. */
public record ManufacturerUpdatedEvent(EventMetadata metadata, ManufacturerPayload payload)
        implements DomainEvent<ManufacturerPayload> {

    public static final String TYPE = "manufacturer.updated";
}
