package com.malphasos.malphasos.client.domain.manager.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una persona pasó a encargarse de una sede o de un área. */
public record ManagerAssignedEvent(EventMetadata metadata, ManagerPayload payload)
        implements DomainEvent<ManagerPayload> {

    public static final String TYPE = "manager.assigned";
}
