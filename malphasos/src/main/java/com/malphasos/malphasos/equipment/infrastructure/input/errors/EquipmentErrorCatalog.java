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
    SERVICE_AREA_NOT_FOUND("ERR_EQUIPMENT_009", "Service area not found");

    private final String code;
    private final String message;

    EquipmentErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
