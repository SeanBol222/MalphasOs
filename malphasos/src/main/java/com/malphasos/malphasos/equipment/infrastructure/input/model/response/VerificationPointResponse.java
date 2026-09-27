package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un punto de verificación de un tipo de equipo.
 *
 * <p>Solo se devuelven los <b>activos</b>: los retirados siguen en la base porque con ellos se hicieron
 * los reportes anteriores, pero no son con qué se verifica hoy y ofrecerlos los pondría a competir.
 */
@Schema(name = "VerificationPointResponse")
public record VerificationPointResponse(
        UUID id,
        @Schema(description = "Valor en el que se mantiene lo constante", example = "100.0000")
        BigDecimal valor,
        @Schema(example = "mmHg") String unidad) {
}
