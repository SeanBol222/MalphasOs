package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe un modelo con ese identificador. */
public class ModelNotFoundException extends RuntimeException {

    public ModelNotFoundException(UUID id) {
        super("No existe un modelo con el identificador " + id);
    }
}
