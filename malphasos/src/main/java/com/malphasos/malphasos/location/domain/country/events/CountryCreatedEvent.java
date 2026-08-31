package com.malphasos.malphasos.location.domain.country.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró un país nuevo. */
public record CountryCreatedEvent(EventMetadata metadata, CountryPayload payload)
        implements DomainEvent<CountryPayload> {

    public static final String TYPE = "country.created";
}
