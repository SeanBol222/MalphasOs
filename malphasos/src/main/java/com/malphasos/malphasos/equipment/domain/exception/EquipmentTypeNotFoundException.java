package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe un tipo de equipo con ese identificador. */
public class EquipmentTypeNotFoundException extends RuntimeException {

    public EquipmentTypeNotFoundException(UUID id) {
        super("No existe un tipo de equipo con el identificador " + id);
    }
}
