package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Desde qué día una orden ejecutada espera aprobación (2026-11-22). */
public record ApprovalStartResponse(
        @Schema(description = "Dia desde el que una ejecutada espera aprobacion; nulo si no hay corte",
                example = "2026-11-02") LocalDate desde) {
}
