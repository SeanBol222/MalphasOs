package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * Un tipo de equipo.
 *
 * <p>{@code verificable} se expone porque al cliente del API le resulta cómodo, pero es derivado:
 * vale verdadero exactamente cuando hay modalidad.
 */
@Builder
@Schema(name = "EquipmentTypeResponse")
public record EquipmentTypeResponse(
        UUID id,
        String nombre,
        String definicionTecnica,
        String recomendacionesCuidado,
        String tecnologiaPredominante,
        Integer voltaje,
        BigDecimal amperaje,
        boolean verificable,
        VerificationMode modalidadVerificacion,
        long valorUnitarioMantenimiento,
        boolean estadoActivo) {
}
