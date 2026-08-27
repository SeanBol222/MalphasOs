package com.malphasos.malphasos.person.domain.exception;

import java.util.UUID;

/** No existe una persona con el identificador solicitado. */
public class PersonNotFoundException extends RuntimeException {

    public PersonNotFoundException(UUID identificador) {
        super("No existe una persona con el identificador " + identificador);
    }

    public PersonNotFoundException(String message) {
        super(message);
    }
}
