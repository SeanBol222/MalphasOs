package com.malphasos.malphasos.report.infrastructure.input.errors;

import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ModelNotFoundException;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.report.infrastructure.input.rest.ServiceReportRestAdapter;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de este módulo al contrato de error del API.
 *
 * <p>Maneja también las de los módulos que este consulta, <b>incluidos los tres eslabones
 * intermedios del catálogo</b> —modelo, equipo y tipo—. Podría parecer que sobran, porque el llamante
 * nunca los nombra: los recorre el servidor para averiguar cómo se verifica el equipo. Y justamente
 * por eso hacen falta: si uno de esos eslabones está retirado o roto, sin declararlo aquí el fallo
 * saldría como un 500 sin explicación. Es el hueco que se descubrió en la tanda del traslado por
 * dejarse una fuera.
 */
@RestControllerAdvice(assignableTypes = ServiceReportRestAdapter.class)
public class ServiceReportControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ServiceReportNotFoundException.class)
    public ServiceReportErrorResponse handleServiceReport(ServiceReportNotFoundException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.SERVICE_REPORT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(WorkOrderNotFoundException.class)
    public ServiceReportErrorResponse handleWorkOrder(WorkOrderNotFoundException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.WORK_ORDER_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ClientEquipmentNotFoundException.class)
    public ServiceReportErrorResponse handleClientEquipment(ClientEquipmentNotFoundException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.CLIENT_EQUIPMENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ModelNotFoundException.class)
    public ServiceReportErrorResponse handleModel(ModelNotFoundException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.MODEL_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(EquipmentNotFoundException.class)
    public ServiceReportErrorResponse handleEquipment(EquipmentNotFoundException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.EQUIPMENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(EquipmentTypeNotFoundException.class)
    public ServiceReportErrorResponse handleEquipmentType(EquipmentTypeNotFoundException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.EQUIPMENT_TYPE_NOT_FOUND, List.of(ex.getMessage()));
    }

    /**
     * El momento no admite la operación: 409 y no 400.
     *
     * <p>Los datos recibidos son válidos y no falta ninguno; lo que choca es cuándo se piden — abrir
     * un reporte en una orden que no ha empezado, tocar uno ya cerrado, o cerrarlo con la
     * verificación a medias.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public ServiceReportErrorResponse handleState(IllegalStateException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.SERVICE_REPORT_STATE_CONFLICT, List.of(ex.getMessage()));
    }

    /** Las reglas del agregado y del servicio llegan como esto. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ServiceReportErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return ServiceReportErrorResponse.of(
                ServiceReportErrorCatalog.INVALID_SERVICE_REPORT_DATA, List.of(ex.getMessage()));
    }
}
