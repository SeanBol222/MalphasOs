package com.malphasos.malphasos.equipment.infrastructure.input.errors;

import lombok.Getter;

/** Códigos de error propios de este contexto. Cuarta copia de la estructura. */
@Getter
public enum EquipmentErrorCatalog {
    MANUFACTURER_NOT_FOUND("ERR_EQUIPMENT_001", "Manufacturer not found"),
    BRAND_NOT_FOUND("ERR_EQUIPMENT_002", "Brand not found"),
    EQUIPMENT_TYPE_NOT_FOUND("ERR_EQUIPMENT_003", "Equipment type not found"),
    EQUIPMENT_NOT_FOUND("ERR_EQUIPMENT_004", "Brand-type association not found"),
    MODEL_NOT_FOUND("ERR_EQUIPMENT_005", "Model not found"),
    CLIENT_EQUIPMENT_NOT_FOUND("ERR_EQUIPMENT_006", "Client equipment not found"),
    INVALID_EQUIPMENT_DATA("ERR_EQUIPMENT_007", "Invalid equipment data");

    private final String code;
    private final String message;

    EquipmentErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
