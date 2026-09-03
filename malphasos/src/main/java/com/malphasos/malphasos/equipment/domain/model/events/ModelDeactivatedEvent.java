package com.malphasos.malphasos.equipment.domain.model.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un modelo dejó de estar disponible. */
public record ModelDeactivatedEvent(EventMetadata metadata, ModelPayload payload) implements DomainEvent<ModelPayload> {

    public static final String TYPE = "model.deactivated";
}
