package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Abrir el reporte de un equipo de una orden. No lleva más datos: el reporte nace vacío. */
public record OpenServiceReportCommand(UUID idOrdenTrabajo, UUID idEquipoCliente) {
}
