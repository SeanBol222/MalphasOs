package com.malphasos.malphasos.person.domain.exception;

/**
 * No existe en Keycloak el usuario sobre el que se quiso operar.
 *
 * <p>No es necesariamente un fallo: una persona registrada con {@code save} existe solo como dato,
 * sin cuenta con la que entrar al sistema, y una cuenta pudo eliminarse a mano desde la consola de
 * administración. Quien orquesta el caso de uso decide si eso interrumpe la operación; en el módulo
 * de personas no lo hace, porque el objetivo de la baja —que nadie pueda entrar con esa
 * identidad— ya se cumple cuando la identidad no existe.
 *
 * <p>Por eso esta excepción se atiende en la capa de aplicación y no llega al cliente del API: no
 * tiene entrada en {@code PersonErrorCatalog}.
 */
public class KeycloakUserNotFoundException extends RuntimeException {

    public KeycloakUserNotFoundException(String message) {
        super(message);
    }

    public KeycloakUserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
