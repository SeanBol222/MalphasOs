package com.malphasos.malphasos.person.domain.exception;

/** Keycloak rechazó la contraseña por su política: largo, o igual al usuario o al correo. */
public class KeycloakPasswordRejectedException extends RuntimeException {

    public KeycloakPasswordRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
