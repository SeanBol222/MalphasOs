package com.malphasos.malphasos.equipment.domain.equipmentType.events;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.shared.domain.events.Payload;

/** Datos de un tipo de equipo que viajan con sus eventos. */
public record EquipmentTypePayload(
        String nombre, VerificationMode modalidadVerificacion, long valorUnitarioMantenimiento)
        implements Payload {
}
