package com.malphasos.malphasos.equipment.domain.protocol.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un protocolo se retiró del catálogo. No estaba asignado a ningún tipo vigente: eso se exige antes. */
public record ProtocolDeactivatedEvent(EventMetadata metadata, ProtocolPayload payload)
        implements DomainEvent<ProtocolPayload> {

    public static final String TYPE = "protocol.deactivated";
}
