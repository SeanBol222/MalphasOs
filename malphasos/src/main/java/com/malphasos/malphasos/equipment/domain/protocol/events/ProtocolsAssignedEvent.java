package com.malphasos.malphasos.equipment.domain.protocol.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los protocolos de un tipo de equipo: cuáles, en qué orden o con qué indicación. */
public record ProtocolsAssignedEvent(EventMetadata metadata, ProtocolsAssignedPayload payload)
        implements DomainEvent<ProtocolsAssignedPayload> {

    public static final String TYPE = "equipment-type.protocols-assigned";
}
