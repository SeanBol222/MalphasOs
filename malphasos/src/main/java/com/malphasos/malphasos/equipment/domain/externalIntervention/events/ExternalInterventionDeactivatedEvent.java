package com.malphasos.malphasos.equipment.domain.externalIntervention.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se retiró del historial una intervención externa mal anotada. */
public record ExternalInterventionDeactivatedEvent(EventMetadata metadata, ExternalInterventionPayload payload)
        implements DomainEvent<ExternalInterventionPayload> {

    public static final String TYPE = "external-intervention.deactivated";
}
