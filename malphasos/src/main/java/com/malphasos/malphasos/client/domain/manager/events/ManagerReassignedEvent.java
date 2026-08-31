package com.malphasos.malphasos.client.domain.manager.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un encargado pasó a hacerse cargo de otra sede o de otra área. */
public record ManagerReassignedEvent(EventMetadata metadata, ManagerPayload payload)
        implements DomainEvent<ManagerPayload> {

    public static final String TYPE = "manager.reassigned";
}
