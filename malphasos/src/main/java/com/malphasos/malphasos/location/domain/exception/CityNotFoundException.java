package com.malphasos.malphasos.location.domain.exception;

import java.util.UUID;

/** No existe una ciudad con ese identificador. */
public class CityNotFoundException extends RuntimeException {

    public CityNotFoundException(UUID id) {
        super("No existe una ciudad con el identificador " + id);
    }
}
