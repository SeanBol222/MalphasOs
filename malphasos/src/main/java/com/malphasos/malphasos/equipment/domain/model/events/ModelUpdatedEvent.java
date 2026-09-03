package com.malphasos.malphasos.equipment.domain.model.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los datos de un modelo. */
public record ModelUpdatedEvent(EventMetadata metadata, ModelPayload payload) implements DomainEvent<ModelPayload> {

    public static final String TYPE = "model.updated";
}
