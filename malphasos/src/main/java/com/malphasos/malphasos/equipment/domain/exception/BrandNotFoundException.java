package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe una marca con ese identificador. */
public class BrandNotFoundException extends RuntimeException {

    public BrandNotFoundException(UUID id) {
        super("No existe una marca con el identificador " + id);
    }
}
