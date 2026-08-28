package com.malphasos.malphasos.person.domain.exception;

/** Ya existe un usuario en Keycloak con esos datos de identificación. */
public class KeycloakUserAlreadyExistsException extends RuntimeException {

    public KeycloakUserAlreadyExistsException(String message) {
        super(message);
    }

    public KeycloakUserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
