package com.malphasos.malphasos.equipment.domain.verificationStandards.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import java.util.UUID;

/** Los patrones de una verificación, en el orden en que llegaron. */
public record VerificationStandardsAssignedPayload(UUID idVerificacion, List<UUID> patrones) implements Payload {

    public VerificationStandardsAssignedPayload {
        patrones = List.copyOf(patrones);
    }
}
