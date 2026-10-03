package com.malphasos.malphasos.equipment.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto. Cuarta copia de la estructura.
 *
 * <p>Un código de "no existe" nunca se comparte con uno de "datos inválidos": salen con estados
 * HTTP distintos, y un cliente que solo mire el código no podría distinguirlos.
 */
@Getter
public enum EquipmentErrorCatalog {
    MANUFACTURER_NOT_FOUND("ERR_EQUIPMENT_001", "Manufacturer not found"),
    BRAND_NOT_FOUND("ERR_EQUIPMENT_002", "Brand not found"),
    EQUIPMENT_TYPE_NOT_FOUND("ERR_EQUIPMENT_003", "Equipment type not found"),
    EQUIPMENT_NOT_FOUND("ERR_EQUIPMENT_004", "Brand-type association not found"),
    MODEL_NOT_FOUND("ERR_EQUIPMENT_005", "Model not found"),
    CLIENT_EQUIPMENT_NOT_FOUND("ERR_EQUIPMENT_006", "Client equipment not found"),
    INVALID_EQUIPMENT_DATA("ERR_EQUIPMENT_007", "Invalid equipment data"),

    // Referencias hacia otros modulos. Llevan codigo propio aunque la excepcion venga de fuera:
    // el cliente del API necesita saber cual de las referencias fallo, y "datos invalidos" no se
    // lo dice.
    COUNTRY_NOT_FOUND("ERR_EQUIPMENT_008", "Country not found"),
    SERVICE_AREA_NOT_FOUND("ERR_EQUIPMENT_009", "Service area not found"),

    // Reglas de negocio que rechazan una operacion valida en sus datos. No entran por
    // INVALID_EQUIPMENT_DATA: el area de destino existe, esta abierta y el identificador es
    // correcto; lo que falla es que la unidad es de otro cliente.
    CROSS_CLIENT_RELOCATION("ERR_EQUIPMENT_010", "Relocation across clients is not allowed"),

    // El catalogo metrologico, del 2026-10-03. Las dos primeras son "no existe" y van con 404; la
    // tercera NO es "datos invalidos" y tampoco "no existe": las dos filas existen y los dos
    // identificadores son correctos, lo que falla es la combinacion. Es el mismo caso que
    // CROSS_CLIENT_RELOCATION, y por eso lleva codigo propio.
    MAGNITUDE_NOT_FOUND("ERR_EQUIPMENT_011", "Magnitude not found"),
    MEASUREMENT_UNIT_NOT_FOUND("ERR_EQUIPMENT_012", "Measurement unit not found"),
    UNIT_OUTSIDE_MAGNITUDE("ERR_EQUIPMENT_013", "The unit does not belong to that magnitude");

    private final String code;
    private final String message;

    EquipmentErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
