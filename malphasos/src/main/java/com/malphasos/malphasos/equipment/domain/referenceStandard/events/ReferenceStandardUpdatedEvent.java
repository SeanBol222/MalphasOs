package com.malphasos.malphasos.equipment.domain.referenceStandard.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se corrigió un equipo patrón o se renovó su certificado. */
public record ReferenceStandardUpdatedEvent(EventMetadata metadata, ReferenceStandardPayload payload)
        implements DomainEvent<ReferenceStandardPayload> {

    public static final String TYPE = "reference-standard.updated";
}
