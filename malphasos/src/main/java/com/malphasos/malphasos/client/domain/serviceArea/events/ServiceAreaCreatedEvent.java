package com.malphasos.malphasos.client.domain.serviceArea.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se abrió un área de servicio dentro de una sede. */
public record ServiceAreaCreatedEvent(EventMetadata metadata, ServiceAreaPayload payload)
        implements DomainEvent<ServiceAreaPayload> {

    public static final String TYPE = "service-area.created";
}
