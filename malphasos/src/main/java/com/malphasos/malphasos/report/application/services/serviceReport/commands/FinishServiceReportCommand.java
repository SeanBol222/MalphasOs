package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Cerrar un reporte. Lo que se exige para poder cerrarlo ya está dentro del reporte. */
public record FinishServiceReportCommand(UUID id) {
}
