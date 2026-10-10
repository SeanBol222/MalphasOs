package com.malphasos.malphasos.equipment.domain.protocol.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se creó un protocolo nuevo en el catálogo. */
public record ProtocolCreatedEvent(EventMetadata metadata, ProtocolPayload payload)
        implements DomainEvent<ProtocolPayload> {

    public static final String TYPE = "protocol.created";
}
