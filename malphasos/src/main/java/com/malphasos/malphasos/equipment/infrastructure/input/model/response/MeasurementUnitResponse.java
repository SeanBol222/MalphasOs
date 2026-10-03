package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Una unidad del catálogo metrológico.
 *
 * <p>No repite la magnitud: se consulta por magnitud, así que quien pregunta ya sabe cuál es.
 */
@Schema(name = "MeasurementUnitResponse")
public record MeasurementUnitResponse(
        UUID id,
        @Schema(description = "Lo que se imprime junto al numero", example = "°C") String simbolo,
        @Schema(example = "grado Celsius") String nombre) {
}
