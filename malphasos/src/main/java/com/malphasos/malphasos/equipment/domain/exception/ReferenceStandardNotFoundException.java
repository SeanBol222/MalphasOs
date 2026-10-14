package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe un equipo patrón con ese identificador. */
public class ReferenceStandardNotFoundException extends RuntimeException {

    public ReferenceStandardNotFoundException(UUID id) {
        super("No existe el equipo patron " + id);
    }
}
