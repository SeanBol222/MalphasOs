package com.malphasos.malphasos.client.domain.headquarter.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron el nombre o la dirección de una sede. */
public record HeadquarterUpdatedEvent(EventMetadata metadata, HeadquarterPayload payload)
        implements DomainEvent<HeadquarterPayload> {

    public static final String TYPE = "headquarter.updated";
}
