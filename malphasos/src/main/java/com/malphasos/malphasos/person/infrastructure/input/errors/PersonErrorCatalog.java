package com.malphasos.malphasos.person.infrastructure.input.errors;

import lombok.Getter;

/**
 * Catálogo de errores propios del módulo de personas.
 *
 * <p>Vive junto al {@code ControllerAdvice} que lo consume, en la capa de entrada: es parte del
 * contrato del API, no del dominio. En el proyecto original estaba en un paquete {@code utils}
 * suelto y su DTO de respuesta en el paquete de dominio.
 */
@Getter
public enum PersonErrorCatalog {

    PERSON_NOT_FOUND("ERR_PERSON_001", "Person not found"),
    INVALID_PERSON_DATA("ERR_PERSON_002", "Invalid person data"),
    KEYCLOAK_USER_ALREADY_EXISTS("ERR_KEYCLOAK_001", "Keycloak user already exists"),
    KEYCLOAK_INVALID_DATA("ERR_KEYCLOAK_002", "Invalid data for Keycloak operation"),
    KEYCLOAK_UNAUTHORIZED("ERR_KEYCLOAK_003", "The service account cannot operate on Keycloak"),
    KEYCLOAK_CONNECTION_ERROR("ERR_KEYCLOAK_004", "Connection error with Keycloak");

    private final String code;
    private final String message;

    PersonErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
