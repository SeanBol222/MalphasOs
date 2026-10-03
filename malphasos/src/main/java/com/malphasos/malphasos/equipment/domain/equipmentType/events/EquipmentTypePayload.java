package com.malphasos.malphasos.equipment.domain.equipmentType.events;

import com.malphasos.malphasos.shared.domain.events.Payload;

/**
 * Datos de un tipo de equipo que viajan con sus eventos.
 *
 * <p><b>Llevaba la modalidad de verificación y ya no puede</b>: desde el 2026-10-03 un tipo tiene
 * varias verificaciones, cada una con la suya, y no hay «la» modalidad del tipo. Lo que queda es el
 * booleano derivado, que es la única afirmación que sigue siendo cierta a este nivel: si a este tipo se
 * le verifica algo o no.
 */
public record EquipmentTypePayload(String nombre, boolean verificable, long valorUnitarioMantenimiento)
        implements Payload {
}
