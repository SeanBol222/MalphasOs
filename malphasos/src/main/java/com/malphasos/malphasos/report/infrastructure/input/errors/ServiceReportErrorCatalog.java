package com.malphasos.malphasos.report.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto. Sexta copia de la misma estructura.
 *
 * <p>Un código de "no existe" nunca se comparte con uno de "datos inválidos": salen con estados HTTP
 * distintos, y un cliente que solo mire el código no podría distinguirlos. Por eso cada referencia
 * hacia otro módulo lleva el suyo.
 */
@Getter
public enum ServiceReportErrorCatalog {
    SERVICE_REPORT_NOT_FOUND("ERR_SERVICE_REPORT_001", "Service report not found"),
    INVALID_SERVICE_REPORT_DATA("ERR_SERVICE_REPORT_002", "Invalid service report data"),

    /**
     * El momento no admite la operación: no es un dato mal escrito.
     *
     * <p>Cubre tres cosas que tienen en común no ser culpa de lo enviado: la orden todavía no ha
     * empezado, el reporte ya está cerrado, y la verificación está a medias.
     */
    SERVICE_REPORT_STATE_CONFLICT("ERR_SERVICE_REPORT_003", "Service report state does not allow it"),

    // Referencias hacia otros modulos. Llevan codigo propio aunque la excepcion venga de fuera: el
    // cliente del API necesita saber cual de las referencias fallo.
    WORK_ORDER_NOT_FOUND("ERR_SERVICE_REPORT_004", "Work order not found"),
    CLIENT_EQUIPMENT_NOT_FOUND("ERR_SERVICE_REPORT_005", "Client equipment not found"),
    MODEL_NOT_FOUND("ERR_SERVICE_REPORT_006", "Model not found"),
    EQUIPMENT_NOT_FOUND("ERR_SERVICE_REPORT_007", "Equipment not found"),
    EQUIPMENT_TYPE_NOT_FOUND("ERR_SERVICE_REPORT_008", "Equipment type not found");

    private final String code;
    private final String message;

    ServiceReportErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
