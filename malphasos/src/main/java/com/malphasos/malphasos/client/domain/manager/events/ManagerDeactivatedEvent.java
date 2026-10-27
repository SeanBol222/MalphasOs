package com.malphasos.malphasos.client.domain.manager.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una persona dejó de encargarse de una sede o de un área, la del payload. El registro permanece. */
public record ManagerDeactivatedEvent(EventMetadata metadata, ManagerPayload payload)
        implements DomainEvent<ManagerPayload> {

    public static final String TYPE = "manager.deactivated";
}
