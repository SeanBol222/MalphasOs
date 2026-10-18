package com.malphasos.malphasos.person.domain.exception;

/**
 * Keycloak rechazó el correo porque otra cuenta ya lo usa: el realm no admite correos repetidos, y
 * con razón, porque se puede entrar con el correo en vez del nombre de usuario.
 */
public class KeycloakEmailInUseException extends RuntimeException {

    public KeycloakEmailInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
