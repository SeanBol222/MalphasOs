package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un accesorio con el que llegó el equipo (V31). */
@Schema(name = "ReportAccessoryResponse")
public record ReportAccessoryResponse(
        int orden, String nombre, boolean conforme, @Schema(description = "Nula si no hay") String observacion) {
}
