package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import java.util.UUID;

/**
 * Marcar un paso del protocolo de un reporte. {@code orden} es el del paso dentro del reporte, el mismo
 * que la prioridad que el protocolo tenía en el tipo al abrirlo. La nota es obligatoria con
 * {@code NO_APLICA}.
 */
public record MarkReportStepCommand(UUID id, int orden, StepState estado, String nota) {
}
