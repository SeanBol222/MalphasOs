package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import java.math.BigDecimal;

/**
 * Alta de un tipo de equipo.
 *
 * <p>No lleva un campo "verificable": lo determina la modalidad. Si viene, el tipo se verifica.
 */
public record CreateEquipmentTypeCommand(
        String nombre,
        String definicionTecnica,
        String recomendacionesCuidado,
        String tecnologiaPredominante,
        Integer voltaje,
        BigDecimal amperaje,
        VerificationMode modalidadVerificacion,
        long valorUnitarioMantenimiento) {
}
