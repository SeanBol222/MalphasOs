package com.malphasos.malphasos.client.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto.
 *
 * <p>Tercera copia de la misma estructura, tras {@code PersonErrorCatalog} y
 * {@code LocationErrorCatalog}. Es la duplicación que la decisión de repetir el manejo de
 * excepciones por módulo asume a cambio de que cada contexto acotado sea dueño de su contrato de
 * error.
 */
@Getter
public enum ClientErrorCatalog {
    CLIENT_NOT_FOUND("ERR_CLIENT_001", "Client not found"),
    HEADQUARTER_NOT_FOUND("ERR_CLIENT_002", "Headquarter not found"),
    SERVICE_AREA_NOT_FOUND("ERR_CLIENT_003", "Service area not found"),
    MANAGER_NOT_FOUND("ERR_CLIENT_004", "Manager not found"),
    INVALID_CLIENT_DATA("ERR_CLIENT_005", "Invalid client data");

    private final String code;
    private final String message;

    ClientErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
