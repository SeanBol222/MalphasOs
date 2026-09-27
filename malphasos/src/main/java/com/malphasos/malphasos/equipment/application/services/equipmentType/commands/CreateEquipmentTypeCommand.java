package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de un tipo de equipo.
 *
 * <p>No lleva un campo "verificable": lo determina la modalidad. Si viene, el tipo se verifica.
 *
 * <p>La cantidad de lecturas y los puntos acompañan a la modalidad porque son la misma decisión, y el
 * agregado exige que sean coherentes: las modalidades constantes las necesitan, la variable no las
 * admite. Ver {@code EquipmentType}.
 */
public record CreateEquipmentTypeCommand(
        String nombre,
        String definicionTecnica,
        String recomendacionesCuidado,
        String tecnologiaPredominante,
        Integer voltaje,
        BigDecimal amperaje,
        VerificationMode modalidadVerificacion,
        Integer cantidadDatos,
        List<VerificationPointCommand> puntosVerificacion,
        long valorUnitarioMantenimiento) {
}
