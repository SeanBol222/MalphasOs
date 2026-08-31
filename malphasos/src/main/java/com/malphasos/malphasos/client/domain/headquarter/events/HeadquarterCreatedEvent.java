package com.malphasos.malphasos.client.domain.headquarter.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un cliente abrió una sede. */
public record HeadquarterCreatedEvent(EventMetadata metadata, HeadquarterPayload payload)
        implements DomainEvent<HeadquarterPayload> {

    public static final String TYPE = "headquarter.created";
}
