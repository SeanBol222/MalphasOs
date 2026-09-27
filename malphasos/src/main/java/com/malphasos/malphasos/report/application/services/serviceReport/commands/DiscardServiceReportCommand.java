package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Retirar un reporte sin borrarlo. */
public record DiscardServiceReportCommand(UUID id) {
}
