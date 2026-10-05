package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** La ficha técnica de un modelo. Cada campo es nulo mientras no se conozca. */
@Schema(name = "TechnicalSheetResponse")
public record TechnicalSheetResponse(
        RiskClass riesgo,
        String caracteristicas,
        String alimentacion,
        @Schema(description = "En V") Integer voltaje,
        @Schema(description = "En W") Integer potencia,
        @Schema(description = "En A") BigDecimal amperaje,
        @Schema(description = "En Hz") Integer frecuencia) {
}
