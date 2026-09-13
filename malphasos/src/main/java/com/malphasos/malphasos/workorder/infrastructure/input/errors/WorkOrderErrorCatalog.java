package com.malphasos.malphasos.workorder.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto. Quinta copia de la misma estructura.
 *
 * <p>Un código de "no existe" nunca se comparte con uno de "datos inválidos": salen con estados
 * HTTP distintos, y un cliente que solo mire el código no podría distinguirlos. Por eso cada
 * referencia hacia otro módulo lleva el suyo.
 */
@Getter
public enum WorkOrderErrorCatalog {
    WORK_ORDER_NOT_FOUND("ERR_WORK_ORDER_001", "Work order not found"),
    INVALID_WORK_ORDER_DATA("ERR_WORK_ORDER_002", "Invalid work order data"),

    /** El estado de la orden no admite la operación: no es un dato mal escrito. */
    WORK_ORDER_STATE_CONFLICT("ERR_WORK_ORDER_003", "Work order state does not allow it"),

    // Referencias hacia otros modulos. Llevan codigo propio aunque la excepcion venga de fuera:
    // el cliente del API necesita saber cual de las referencias fallo.
    CLIENT_NOT_FOUND("ERR_WORK_ORDER_004", "Client not found"),
    HEADQUARTER_NOT_FOUND("ERR_WORK_ORDER_005", "Headquarter not found"),
    SERVICE_AREA_NOT_FOUND("ERR_WORK_ORDER_006", "Service area not found"),
    CLIENT_EQUIPMENT_NOT_FOUND("ERR_WORK_ORDER_007", "Client equipment not found"),
    PERSON_NOT_FOUND("ERR_WORK_ORDER_008", "Person not found");

    private final String code;
    private final String message;

    WorkOrderErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
