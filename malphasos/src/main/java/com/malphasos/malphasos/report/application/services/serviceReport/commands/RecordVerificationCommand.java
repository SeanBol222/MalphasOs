package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.List;
import java.util.UUID;

/**
 * Registrar la verificación metrológica de un reporte, con todas sus lecturas.
 *
 * <p>Llega la tabla completa y sustituye la anterior: una verificación se corrige entera, no celda a
 * celda. Ver {@code ServiceReport.recordVerification}.
 */
public record RecordVerificationCommand(UUID id, List<VerificationReadingCommand> lecturas) {
}
