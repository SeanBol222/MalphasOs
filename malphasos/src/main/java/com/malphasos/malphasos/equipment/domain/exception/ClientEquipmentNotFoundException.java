package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe una unidad de cliente con ese identificador. */
public class ClientEquipmentNotFoundException extends RuntimeException {

    public ClientEquipmentNotFoundException(UUID id) {
        super("No existe una unidad de cliente con el identificador " + id);
    }
}
