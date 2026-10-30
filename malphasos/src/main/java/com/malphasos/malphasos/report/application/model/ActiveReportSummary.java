package com.malphasos.malphasos.report.application.model;

import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import java.util.UUID;

/**
 * Lo que dice de una orden un reporte activo, sin cargar su contenido (V50): que su equipo está en ella,
 * con qué reporte y si ya se cerró. Es lo que piden el listado de órdenes y la agenda, de decenas de
 * órdenes a la vez.
 */
public record ActiveReportSummary(UUID id, UUID idOrdenTrabajo, UUID idEquipoCliente, ReportState estado) {
}
