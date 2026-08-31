package com.malphasos.malphasos.location.domain.city.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una ciudad pasó a llamarse de otra forma. */
public record CityRenamedEvent(EventMetadata metadata, CityPayload payload)
        implements DomainEvent<CityPayload> {

    public static final String TYPE = "city.renamed";
}
