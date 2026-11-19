package com.malphasos.malphasos.equipment.domain.verificationStandards.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Cambiaron los patrones con que se hace una verificación. */
public record VerificationStandardsAssignedEvent(EventMetadata metadata, VerificationStandardsAssignedPayload payload)
        implements DomainEvent<VerificationStandardsAssignedPayload> {

    public static final String TYPE = "equipment-type.verification-standards-assigned";
}
