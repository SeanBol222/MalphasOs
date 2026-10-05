package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Un tipo de equipo.
 *
 * <p>{@code verificable} se expone porque al cliente del API le resulta cómodo, pero es derivado:
 * vale verdadero exactamente cuando hay al menos una verificación activa.
 *
 * <p>{@code verificaciones} trae solo las <b>activas</b>, y cada una sus puntos activos. Las retiradas
 * se quedan en la base porque con ellas se firmaron reportes, pero no son con qué se verifica hoy.
 */
@Builder
@Schema(name = "EquipmentTypeResponse")
public record EquipmentTypeResponse(
        UUID id,
        String nombre,
        String definicionTecnica,
        String recomendacionesCuidado,
        String tecnologiaPredominante,
        String uso,
        String limpiezaCotidiana,
        boolean verificable,
        List<TypeVerificationResponse> verificaciones,
        long valorUnitarioMantenimiento,
        boolean estadoActivo) {
}
