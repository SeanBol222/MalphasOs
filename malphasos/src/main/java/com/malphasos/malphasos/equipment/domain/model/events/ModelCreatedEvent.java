package com.malphasos.malphasos.equipment.domain.model.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró un modelo nuevo. */
public record ModelCreatedEvent(EventMetadata metadata, ModelPayload payload) implements DomainEvent<ModelPayload> {

    public static final String TYPE = "model.created";
}
