package com.malphasos.malphasos.equipment.domain.externalIntervention.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se anotó en el historial de un equipo algo que le hizo otra empresa. */
public record ExternalInterventionRecordedEvent(EventMetadata metadata, ExternalInterventionPayload payload)
        implements DomainEvent<ExternalInterventionPayload> {

    public static final String TYPE = "external-intervention.recorded";
}
