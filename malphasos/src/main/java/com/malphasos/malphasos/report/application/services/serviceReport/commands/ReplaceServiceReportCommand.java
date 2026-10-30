package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Sustituir un reporte cerrado: retirarlo y abrir el borrador del mismo equipo en un paso (V50). */
public record ReplaceServiceReportCommand(UUID id) {
}
