package com.malphasos.malphasos.person.domain.exception;

/** No se pudo establecer comunicación con Keycloak. */
public class KeycloakConnectionException extends RuntimeException {

    public KeycloakConnectionException(String message) {
        super(message);
    }

    public KeycloakConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
