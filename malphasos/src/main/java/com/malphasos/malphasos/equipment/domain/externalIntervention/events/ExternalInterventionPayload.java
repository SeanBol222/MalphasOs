package com.malphasos.malphasos.equipment.domain.externalIntervention.events;

import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.time.LocalDate;
import java.util.UUID;

/** Lo que un evento de una intervención externa cuenta de ella. */
public record ExternalInterventionPayload(
        UUID idEquipoCliente, LocalDate realizada, ExternalServiceType tipoServicio, String empresa)
        implements Payload {
}
