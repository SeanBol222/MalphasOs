package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró un cliente nuevo. */
public record ClientCreatedEvent(EventMetadata metadata, ClientPayload payload) implements DomainEvent<ClientPayload> {

    public static final String TYPE = "client.created";
}
