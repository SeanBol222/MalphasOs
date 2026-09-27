package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Un valor constante en el que se verifica un tipo de equipo.
 *
 * <p>No lleva identificador a propósito: los puntos no se editan uno a uno, se manda la lista completa
 * y el tipo retira los anteriores. Declarar cómo se verifica algo es una sola decisión.
 *
 * <p><b>El valor admite negativos</b>: un congelador se verifica a −20 °C. Y la unidad es obligatoria
 * porque un número sin unidad no se puede escribir en un reporte.
 */
@Schema(name = "VerificationPointRequest")
public record VerificationPointRequest(
        @NotNull(message = "El punto necesita su valor")
        @Digits(integer = 8, fraction = 4,
                message = "El valor admite hasta 8 enteros y 4 decimales")
        @Schema(description = "Valor en el que se mantiene lo constante. Admite negativos",
                example = "100.0")
        BigDecimal valor,

        @NotBlank(message = "El punto necesita su unidad")
        @Size(max = 20, message = "La unidad no puede pasar de 20 caracteres")
        @Schema(example = "mmHg")
        String unidad) {
}
