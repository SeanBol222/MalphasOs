package com.malphasos.malphasos.location.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto.
 *
 * <p>Se repite la estructura de {@code PersonErrorCatalog} en vez de extraer una base común: cada
 * contexto acotado es dueño de su contrato de error y puede cambiarlo sin arrastrar a los demás.
 * Es una duplicación deliberada de forma, no de comportamiento.
 */
@Getter
public enum LocationErrorCatalog {
    COUNTRY_NOT_FOUND("ERR_LOCATION_001", "Country not found"),
    CITY_NOT_FOUND("ERR_LOCATION_002", "City not found"),
    INVALID_LOCATION_DATA("ERR_LOCATION_003", "Invalid location data");

    private final String code;
    private final String message;

    LocationErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
