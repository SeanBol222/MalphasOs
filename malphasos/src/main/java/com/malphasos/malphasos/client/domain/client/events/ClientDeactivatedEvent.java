package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un cliente dejó de estar activo. El registro permanece. */
public record ClientDeactivatedEvent(EventMetadata metadata, ClientPayload payload) implements DomainEvent<ClientPayload> {

    public static final String TYPE = "client.deactivated";
}
