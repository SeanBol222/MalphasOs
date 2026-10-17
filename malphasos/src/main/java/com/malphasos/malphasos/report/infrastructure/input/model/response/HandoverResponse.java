package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Quién recibió el equipo al entregarlo (V31). */
@Schema(name = "HandoverResponse")
public record HandoverResponse(
        String nombre, @Schema(description = "Nula si no se dio") String cedula,
        @Schema(description = "Nulo si no se dio") String cargo) {
}
