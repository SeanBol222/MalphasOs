package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe un fabricante con ese identificador. */
public class ManufacturerNotFoundException extends RuntimeException {

    public ManufacturerNotFoundException(UUID id) {
        super("No existe un fabricante con el identificador " + id);
    }
}
