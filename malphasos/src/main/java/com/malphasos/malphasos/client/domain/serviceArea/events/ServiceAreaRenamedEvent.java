package com.malphasos.malphasos.client.domain.serviceArea.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un área de servicio pasó a llamarse de otra forma. */
public record ServiceAreaRenamedEvent(EventMetadata metadata, ServiceAreaPayload payload)
        implements DomainEvent<ServiceAreaPayload> {

    public static final String TYPE = "service-area.renamed";
}
