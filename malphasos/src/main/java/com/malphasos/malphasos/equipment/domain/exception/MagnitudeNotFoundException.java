package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe una magnitud con ese identificador. */
public class MagnitudeNotFoundException extends RuntimeException {

    public MagnitudeNotFoundException(UUID id) {
        super("No existe una magnitud con el identificador " + id);
    }
}
