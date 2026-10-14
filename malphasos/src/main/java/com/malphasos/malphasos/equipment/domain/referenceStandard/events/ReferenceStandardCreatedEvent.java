package com.malphasos.malphasos.equipment.domain.referenceStandard.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró un equipo patrón en el catálogo. */
public record ReferenceStandardCreatedEvent(EventMetadata metadata, ReferenceStandardPayload payload)
        implements DomainEvent<ReferenceStandardPayload> {

    public static final String TYPE = "reference-standard.created";
}
