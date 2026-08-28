package com.malphasos.malphasos.person.domain.exception;

/** El cliente administrativo no tiene permisos para la operación solicitada en Keycloak. */
public class KeycloakUnauthorizedException extends RuntimeException {

    public KeycloakUnauthorizedException(String message) {
        super(message);
    }

    public KeycloakUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
