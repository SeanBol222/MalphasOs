package com.malphasos.malphasos.report.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una lectura de la verificación.
 *
 * <p><b>La unidad solo se envía cuando no hay punto.</b> Si la lectura declara un punto, la unidad es
 * la de ese punto y el servidor la copia de allí: declararla permitiría imprimir un reporte con una
 * unidad que nadie midió.
 */
@Schema(name = "VerificationReadingRequest")
public record VerificationReadingRequest(
        @Schema(description = "Punto en el que se tomo. Nulo solo con patron y equipo variables")
        UUID idPuntoVerificacion,

        @Schema(description = "Cual de las N lecturas de ese punto es")
        @Min(value = 1, message = "La lectura se numera desde 1")
        @Max(value = 100, message = "No se admiten mas de 100 lecturas por punto")
        int secuencia,

        @NotNull(message = "El valor del patron es obligatorio") BigDecimal valorPatron,

        @NotNull(message = "El valor del equipo es obligatorio") BigDecimal valorEquipo,

        @Schema(description = "Solo si la lectura no tiene punto; con punto se toma la del punto")
        String unidadSinPunto) {
}
