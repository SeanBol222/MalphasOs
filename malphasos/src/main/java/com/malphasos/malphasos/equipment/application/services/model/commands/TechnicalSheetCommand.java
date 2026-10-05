package com.malphasos.malphasos.equipment.application.services.model.commands;

import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import java.math.BigDecimal;

/**
 * La ficha técnica de un modelo tal como llega de fuera, sin validar.
 *
 * <p>Se valida al convertirla en {@link TechnicalSheet}, que es donde vive la regla. Todo es
 * opcional.
 */
public record TechnicalSheetCommand(
        RiskClass riesgo,
        String caracteristicas,
        String alimentacion,
        Integer voltaje,
        Integer potencia,
        BigDecimal amperaje,
        Integer frecuencia) {

    /** La ficha del dominio. Una ficha que no llega es la ficha vacía. */
    public static TechnicalSheet toDomain(TechnicalSheetCommand ficha) {
        return ficha == null
                ? TechnicalSheet.EMPTY
                : TechnicalSheet.of(ficha.riesgo(), ficha.caracteristicas(), ficha.alimentacion(),
                        ficha.voltaje(), ficha.potencia(), ficha.amperaje(), ficha.frecuencia());
    }
}
