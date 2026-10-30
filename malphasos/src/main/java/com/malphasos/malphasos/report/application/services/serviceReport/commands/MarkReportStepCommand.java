package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import java.util.UUID;

/**
 * Marcar un paso del protocolo de un reporte, por el protocolo del que salió: un reporte no lo repite.
 * Hasta V51 era por su orden, que el reporte copiaba al abrirse. La nota es obligatoria con
 * {@code NO_APLICA}.
 */
public record MarkReportStepCommand(UUID id, UUID idProtocolo, StepState estado, String nota) {
}
