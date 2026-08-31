package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una persona pasó a representar legalmente a un cliente. */
public record LegalRepresentativeAppointedEvent(EventMetadata metadata, LegalRepresentativePayload payload)
        implements DomainEvent<LegalRepresentativePayload> {

    public static final String TYPE = "client.representative-appointed";
}
