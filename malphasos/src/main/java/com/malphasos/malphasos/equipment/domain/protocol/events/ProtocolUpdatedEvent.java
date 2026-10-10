package com.malphasos.malphasos.equipment.domain.protocol.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un protocolo cambió de nombre o de descripción. Los reportes que ya lo copiaron no cambian. */
public record ProtocolUpdatedEvent(EventMetadata metadata, ProtocolPayload payload)
        implements DomainEvent<ProtocolPayload> {

    public static final String TYPE = "protocol.updated";
}
