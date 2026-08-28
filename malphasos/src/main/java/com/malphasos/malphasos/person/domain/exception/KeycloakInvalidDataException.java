package com.malphasos.malphasos.person.domain.exception;

/** Keycloak rechazó los datos enviados para crear o modificar el usuario. */
public class KeycloakInvalidDataException extends RuntimeException {

    public KeycloakInvalidDataException(String message) {
        super(message);
    }

    public KeycloakInvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
