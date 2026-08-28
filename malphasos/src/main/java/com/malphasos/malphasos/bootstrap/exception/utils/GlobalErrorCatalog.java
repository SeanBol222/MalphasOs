package com.malphasos.malphasos.bootstrap.exception.utils;

import lombok.Getter;

/**
 * Catálogo de errores transversales de la aplicación.
 *
 * <p>Cada constante asocia un código estable, pensado para que los clientes del API puedan
 * identificar el error sin depender del mensaje, con una descripción legible.
 *
 * <p>Aquí solo viven los errores que no pertenecen a ningún módulo de negocio en particular:
 * fallos de infraestructura y de validación de entrada. Cada módulo define su propio catálogo
 * para sus errores de dominio.
 */
@Getter
public enum GlobalErrorCatalog {

    /**
     * Fallo al acceder a la base de datos durante una operación de persistencia.
     */
    DATABASE_ERROR("ERR_DATABASE_001", "Database error"),

    /**
     * Los datos de entrada no cumplen con las validaciones declaradas en la petición.
     */
    INVALID_DATA("ERR_INVALID_DATA_002", "Invalid data"),

    /**
     * Los datos chocan con una restricción de integridad de la base: clave repetida, referencia
     * inexistente o valor fuera del catálogo permitido.
     */
    DATA_CONFLICT("ERR_DATA_CONFLICT_003", "Data conflicts with an existing constraint");

    private final String code;
    private final String message;

    GlobalErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
