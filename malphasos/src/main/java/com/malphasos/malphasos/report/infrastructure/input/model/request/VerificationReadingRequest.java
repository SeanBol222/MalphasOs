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
 * <p><b>La unidad ya no se envía nunca</b> (2026-10-03). La declara la verificación, de modo que el
 * servidor la copia de allí tanto si la lectura tiene punto como si no — antes había que mandarla en el
 * caso sin punto, y eso permitía imprimir un reporte con una unidad que nadie midió.
 *
 * <p><b>Y la verificación es obligatoria</b>: es lo único que dice qué se midió cuando no hay punto, y
 * un termohigrómetro puede verificar temperatura y humedad las dos sin puntos.
 */
@Schema(name = "VerificationReadingRequest")
public record VerificationReadingRequest(
        @NotNull(message = "La lectura declara a que verificacion del tipo pertenece")
        @Schema(description = "Verificacion activa del tipo del equipo a la que pertenece la lectura")
        UUID idVerificacion,

        @Schema(description = "Punto en el que se tomo. Nulo solo si esa verificacion usa patron y"
                + " equipo variables")
        UUID idPuntoVerificacion,

        @Schema(description = "Cual de las N lecturas de ese punto es")
        @Min(value = 1, message = "La lectura se numera desde 1")
        @Max(value = 100, message = "No se admiten mas de 100 lecturas por punto")
        int secuencia,

        @NotNull(message = "El valor del patron es obligatorio") BigDecimal valorPatron,

        @NotNull(message = "El valor del equipo es obligatorio") BigDecimal valorEquipo) {
}
