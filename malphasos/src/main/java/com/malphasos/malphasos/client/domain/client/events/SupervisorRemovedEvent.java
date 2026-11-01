package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una persona dejó de supervisar a un cliente. */
public record SupervisorRemovedEvent(EventMetadata metadata, SupervisorPayload payload)
        implements DomainEvent<SupervisorPayload> {

    public static final String TYPE = "client.supervisor-removed";
}
