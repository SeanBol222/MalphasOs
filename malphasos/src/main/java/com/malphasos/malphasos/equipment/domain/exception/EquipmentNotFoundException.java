package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe una asociacion marca-tipo con ese identificador. */
public class EquipmentNotFoundException extends RuntimeException {

    public EquipmentNotFoundException(UUID id) {
        super("No existe una asociacion marca-tipo con el identificador " + id);
    }
}
