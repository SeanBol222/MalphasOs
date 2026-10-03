package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Un valor constante en el que se verifica algo.
 *
 * <p>No lleva identificador a propósito: los puntos no se editan uno a uno, se manda la lista completa
 * y el tipo retira los anteriores. Declarar qué se verifica es una sola decisión.
 *
 * <p><b>Ya no lleva unidad</b> (2026-10-03): la declara su verificación una sola vez. Antes había que
 * repetirla en cada punto, y dos puntos hermanos podían contradecirse.
 *
 * <p><b>El valor admite negativos</b>: un congelador se verifica a −20 °C.
 */
@Schema(name = "VerificationPointRequest")
public record VerificationPointRequest(
        @NotNull(message = "El punto necesita su valor")
        @Digits(integer = 8, fraction = 4,
                message = "El valor admite hasta 8 enteros y 4 decimales")
        @Schema(description = "Valor en el que se mantiene lo constante. Admite negativos",
                example = "100.0")
        BigDecimal valor) {
}
