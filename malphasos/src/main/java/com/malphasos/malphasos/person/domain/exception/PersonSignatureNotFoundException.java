package com.malphasos.malphasos.person.domain.exception;

import java.util.UUID;

/** Esa persona todavía no ha guardado su firma. */
public class PersonSignatureNotFoundException extends RuntimeException {

    public PersonSignatureNotFoundException(UUID identificador) {
        super("La persona " + identificador + " todavia no ha guardado su firma");
    }
}
