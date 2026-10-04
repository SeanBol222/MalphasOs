package com.malphasos.malphasos.equipment.application.services.intervention.commands;

import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lo que hace falta para anotar una intervención en la hoja de vida de un equipo.
 *
 * <p>Los tres últimos campos son el contenido de la copia congelada: quien los manda los ha leído del
 * reporte en el momento de cerrarse, y a partir de aquí no vuelven a consultarse.
 */
public record RecordInterventionCommand(
        UUID idEquipoCliente,
        UUID idReporteServicio,
        LocalDateTime fechaServicio,
        InterventionType tipoServicio,
        InterventionResult resultado) {
}
