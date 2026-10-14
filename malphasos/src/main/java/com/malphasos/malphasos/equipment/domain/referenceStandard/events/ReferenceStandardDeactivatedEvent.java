package com.malphasos.malphasos.equipment.domain.referenceStandard.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se retiró un equipo patrón del catálogo. */
public record ReferenceStandardDeactivatedEvent(EventMetadata metadata, ReferenceStandardPayload payload)
        implements DomainEvent<ReferenceStandardPayload> {

    public static final String TYPE = "reference-standard.deactivated";
}
