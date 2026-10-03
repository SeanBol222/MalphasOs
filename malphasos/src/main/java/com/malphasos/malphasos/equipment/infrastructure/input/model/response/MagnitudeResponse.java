package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Una magnitud del catálogo metrológico. */
@Schema(name = "MagnitudeResponse")
public record MagnitudeResponse(
        UUID id,
        @Schema(description = "Llave natural, estable y sin acentos", example = "temperatura")
        String codigo,
        @Schema(example = "Temperatura") String nombre) {
}
