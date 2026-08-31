package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los datos de un cliente. */
public record ClientUpdatedEvent(EventMetadata metadata, ClientPayload payload) implements DomainEvent<ClientPayload> {

    public static final String TYPE = "client.updated";
}
