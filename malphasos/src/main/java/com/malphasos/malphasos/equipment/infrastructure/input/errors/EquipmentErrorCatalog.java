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
    UNIT_OUTSIDE_MAGNITUDE("ERR_EQUIPMENT_013", "The unit does not belong to that magnitude"),

    // Los protocolos de mantenimiento, del 2026-10-10. El primero es "no existe"; los otros dos son
    // conflictos con el estado actual —otro protocolo ya tiene ese nombre, o este lo usan tipos
    // vigentes—, no datos mal formados, y por eso no entran por INVALID_EQUIPMENT_DATA.
    PROTOCOL_NOT_FOUND("ERR_EQUIPMENT_014", "Protocol not found"),
    PROTOCOL_NAME_TAKEN("ERR_EQUIPMENT_015", "A protocol with that name already exists"),
    PROTOCOL_IN_USE("ERR_EQUIPMENT_016", "The protocol is assigned to active equipment types"),
    // El catálogo de equipos patrón, del 2026-10-14.
    REFERENCE_STANDARD_NOT_FOUND("ERR_EQUIPMENT_017", "Reference standard not found"),
    REFERENCE_STANDARD_SERIAL_TAKEN("ERR_EQUIPMENT_018", "A reference standard with that serial already exists"),
    REFERENCE_STANDARD_INTERNAL_CODE_TAKEN("ERR_EQUIPMENT_019",
            "A reference standard with that internal code already exists"),
    // La hoja de vida en PDF, del 2026-10-18.
    LIFE_SHEET_PDF_UNAVAILABLE("ERR_EQUIPMENT_020", "The life sheet PDF could not be generated"),

    // V50: como CROSS_CLIENT_RELOCATION, el area de destino es valida y lo que choca es el estado de la
    // unidad, que esta en una orden sin ejecutar.
    RELOCATION_DURING_WORK_ORDER("ERR_EQUIPMENT_021", "The unit is in a work order that has not been executed"),

    // V60: los patrones de cada verificacion. Una verificacion que no es de ese tipo es "no existe", con
    // codigo propio para no confundirla con el tipo que si existe.
    VERIFICATION_NOT_FOUND("ERR_EQUIPMENT_022", "The equipment type has no such verification");

    private final String code;
    private final String message;

    EquipmentErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
