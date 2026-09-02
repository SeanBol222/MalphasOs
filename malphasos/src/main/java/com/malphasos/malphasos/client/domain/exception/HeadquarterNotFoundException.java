package com.malphasos.malphasos.client.domain.exception;

import java.util.UUID;

/** No existe una sede con ese identificador. */
public class HeadquarterNotFoundException extends RuntimeException {

    public HeadquarterNotFoundException(UUID id) {
        super("No existe una sede con el identificador " + id);
    }
}
