package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cambio de las características de un tipo. Un campo nulo deja el valor como está.
 *
 * <p>La modalidad de verificación no aparece: cambia lo que el tipo es y tiene operación propia.
 */
public record UpdateEquipmentTypeCommand(
        UUID id,
        String nombre,
        String definicionTecnica,
        String recomendacionesCuidado,
        String tecnologiaPredominante,
        Integer voltaje,
        BigDecimal amperaje,
        Long valorUnitarioMantenimiento) {
}
