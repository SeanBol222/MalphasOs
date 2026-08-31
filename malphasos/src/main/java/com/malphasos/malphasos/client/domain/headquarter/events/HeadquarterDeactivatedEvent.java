package com.malphasos.malphasos.client.domain.headquarter.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una sede dejó de operar. El registro permanece. */
public record HeadquarterDeactivatedEvent(EventMetadata metadata, HeadquarterPayload payload)
        implements DomainEvent<HeadquarterPayload> {

    public static final String TYPE = "headquarter.deactivated";
}
