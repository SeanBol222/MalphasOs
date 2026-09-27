package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Un tipo de equipo.
 *
 * <p>{@code verificable} se expone porque al cliente del API le resulta cómodo, pero es derivado:
 * vale verdadero exactamente cuando hay modalidad.
 *
 * <p>{@code cantidadDatos} son las lecturas <b>por punto</b>, no en total, y {@code puntosVerificacion}
 * trae solo los activos. Las dos vienen vacías cuando el tipo no se verifica o cuando patrón y equipo
 * varían.
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
        @Schema(description = "Lecturas por punto. Solo con modalidad constante", example = "3")
        Integer cantidadDatos,
        List<VerificationPointResponse> puntosVerificacion,
        long valorUnitarioMantenimiento,
        boolean estadoActivo) {
}
