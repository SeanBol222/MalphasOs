package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de un tipo de equipo.
 *
 * <p>No lleva un campo "verificable": lo determina la lista de verificaciones. Si trae alguna, el tipo
 * se verifica.
 *
 * <p><b>Llevaba modalidad, cantidad de lecturas y puntos sueltos</b> hasta el 2026-10-03, porque se
 * daba por supuesto que un aparato mide una sola cosa. Ahora esos tres datos son de cada
 * {@link TypeVerificationCommand}, y un termohigrómetro manda dos.
 */
public record CreateEquipmentTypeCommand(
        String nombre,
        String definicionTecnica,
        String recomendacionesCuidado,
        String tecnologiaPredominante,
        Integer voltaje,
        BigDecimal amperaje,
        List<TypeVerificationCommand> verificaciones,
        long valorUnitarioMantenimiento) {
}
