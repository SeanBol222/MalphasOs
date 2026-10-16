package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** El avance de los reportes de una orden: lo que el listado de órdenes enseña en cada fila. */
@Schema(name = "WorkOrderReportProgressResponse")
public record WorkOrderReportProgressResponse(
        UUID idOrdenTrabajo,
        @Schema(description = "Equipos con su reporte vivo cerrado") long cerrados,
        @Schema(description = "Equipos con su reporte vivo en borrador") long abiertos) {
}
