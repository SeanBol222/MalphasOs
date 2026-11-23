package com.malphasos.malphasos.equipment.application.services.externalIntervention.commands;

import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDate;
import java.util.UUID;

/** Anotar en el historial de un equipo algo que le hizo otra empresa. */
public record RecordExternalInterventionCommand(
        UUID idEquipoCliente,
        LocalDate realizada,
        ExternalServiceType tipoServicio,
        String descripcion,
        String empresa,
        InterventionResult resultado,
        String documento) {
}
