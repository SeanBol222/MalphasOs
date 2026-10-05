package com.malphasos.malphasos.client.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto.
 *
 * <p>Tercera copia de la misma estructura, tras {@code PersonErrorCatalog} y
 * {@code LocationErrorCatalog}. Es la duplicación que la decisión de repetir el manejo de
 * excepciones por módulo asume a cambio de que cada contexto acotado sea dueño de su contrato de
 * error.
 *
 * <p>Un código de "no existe" nunca se comparte con uno de "datos inválidos": salen con estados
 * HTTP distintos, y un cliente que solo mire el código no podría distinguirlos.
 */
@Getter
public enum ClientErrorCatalog {
    CLIENT_NOT_FOUND("ERR_CLIENT_001", "Client not found"),
    HEADQUARTER_NOT_FOUND("ERR_CLIENT_002", "Headquarter not found"),
    SERVICE_AREA_NOT_FOUND("ERR_CLIENT_003", "Service area not found"),
    MANAGER_NOT_FOUND("ERR_CLIENT_004", "Manager not found"),
    INVALID_CLIENT_DATA("ERR_CLIENT_005", "Invalid client data"),

    // Referencias hacia otros modulos. Llevan codigo propio aunque la excepcion venga de fuera:
    // el cliente del API necesita saber cual de las referencias fallo, y "datos invalidos" no se
    // lo dice.
    CITY_NOT_FOUND("ERR_CLIENT_006", "City not found"),
    PERSON_NOT_FOUND("ERR_CLIENT_007", "Person not found"),
    // Un conflicto y no datos invalidos: la sigla esta bien escrita, pero la tiene otro cliente.
    ACRONYM_TAKEN("ERR_CLIENT_008", "Client acronym already in use");

    private final String code;
    private final String message;

    ClientErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
