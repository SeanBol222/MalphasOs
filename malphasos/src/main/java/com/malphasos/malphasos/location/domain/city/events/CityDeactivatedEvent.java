package com.malphasos.malphasos.location.domain.city.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una ciudad dejó de estar disponible. El registro permanece: el borrado es lógico. */
public record CityDeactivatedEvent(EventMetadata metadata, CityPayload payload)
        implements DomainEvent<CityPayload> {

    public static final String TYPE = "city.deactivated";
}
