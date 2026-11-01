package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/**
 * Una persona pasó a supervisar a un cliente, o cambió el cargo con que lo supervisa. Hasta V53 era el
 * nombramiento de un representante legal, que pasó a ser esto.
 */
public record SupervisorAppointedEvent(EventMetadata metadata, SupervisorPayload payload)
        implements DomainEvent<SupervisorPayload> {

    public static final String TYPE = "client.supervisor-appointed";
}
