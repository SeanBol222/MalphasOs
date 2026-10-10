package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe un protocolo con ese identificador. */
public class ProtocolNotFoundException extends RuntimeException {

    public ProtocolNotFoundException(UUID id) {
        super("No existe un protocolo con el identificador " + id);
    }
}
