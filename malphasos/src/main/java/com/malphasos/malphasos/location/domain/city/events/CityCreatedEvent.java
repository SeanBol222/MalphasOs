package com.malphasos.malphasos.location.domain.city.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró una ciudad nueva. */
public record CityCreatedEvent(EventMetadata metadata, CityPayload payload)
        implements DomainEvent<CityPayload> {

    public static final String TYPE = "city.created";
}
