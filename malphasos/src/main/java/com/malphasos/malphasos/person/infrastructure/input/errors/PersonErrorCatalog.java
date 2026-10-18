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
    SIGNATURE_NOT_FOUND("ERR_PERSON_003", "The person has not saved a signature"),
    PRIMARY_EMAIL_REMOVAL("ERR_PERSON_004", "The account email cannot be removed: choose another one first"),
    KEYCLOAK_USER_ALREADY_EXISTS("ERR_KEYCLOAK_001", "Keycloak user already exists"),
    KEYCLOAK_INVALID_DATA("ERR_KEYCLOAK_002", "Invalid data for Keycloak operation"),
    KEYCLOAK_UNAUTHORIZED("ERR_KEYCLOAK_003", "The service account cannot operate on Keycloak"),
    KEYCLOAK_CONNECTION_ERROR("ERR_KEYCLOAK_004", "Connection error with Keycloak"),
    KEYCLOAK_EMAIL_IN_USE("ERR_KEYCLOAK_005", "Another account already uses that email"),
    KEYCLOAK_PASSWORD_REJECTED("ERR_KEYCLOAK_006", "The password does not meet the policy"),
    KEYCLOAK_ACCOUNT_NOT_FOUND("ERR_KEYCLOAK_007", "The person has no account in the identity provider");

    private final String code;
    private final String message;

    PersonErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
