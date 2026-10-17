package com.malphasos.malphasos.report.infrastructure.input.model.response;

import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import io.swagger.v3.oas.annotations.media.Schema;

/** Una verificación al ingreso (V31). */
@Schema(name = "IntakeCheckResponse")
public record IntakeCheckResponse(IntakeState estado, @Schema(description = "Nula si no hay") String observacion) {
}
