package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * La ficha técnica de un modelo: lo que dice su placa y su registro sanitario. Todo es opcional, y al
 * corregirla se manda entera: lo que no venga queda vacío.
 */
@Schema(name = "TechnicalSheetRequest")
public record TechnicalSheetRequest(
        @Schema(description = "Clasificacion por riesgo del dispositivo medico") RiskClass riesgo,
        @Size(max = 250, message = "Las caracteristicas no pueden pasar de 250 caracteres")
        String caracteristicas,
        @Size(max = 50, message = "La alimentacion no puede pasar de 50 caracteres")
        @Schema(description = "De donde toma la energia", example = "Red electrica")
        String alimentacion,
        @Positive(message = "El voltaje es positivo") @Schema(description = "En V") Integer voltaje,
        @Positive(message = "La potencia es positiva") @Schema(description = "En W") Integer potencia,
        @Positive(message = "El amperaje es positivo")
        @Digits(integer = 6, fraction = 2, message = "El amperaje admite dos decimales")
        @Schema(description = "En A") BigDecimal amperaje,
        @Positive(message = "La frecuencia es positiva") @Schema(description = "En Hz") Integer frecuencia) {
}
