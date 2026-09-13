package com.malphasos.malphasos.workorder.infrastructure.input.errors;

import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.infrastructure.input.rest.WorkOrderRestAdapter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de este módulo al contrato de error del API.
 *
 * <p>Maneja también las de los cuatro módulos que este consulta. Llegan por su controlador y de
 * otro modo escaparían al manejador transversal como un 500, que es el hueco que se descubrió en la
 * tanda del traslado por no declarar una de ellas. Cada una lleva su propio código: el 404 dice que
 * algo no existe, y el código dice qué.
 */
@RestControllerAdvice(assignableTypes = WorkOrderRestAdapter.class)
public class WorkOrderControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(WorkOrderNotFoundException.class)
    public WorkOrderErrorResponse handleWorkOrder(WorkOrderNotFoundException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.WORK_ORDER_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ClientNotFoundException.class)
    public WorkOrderErrorResponse handleClient(ClientNotFoundException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.CLIENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(HeadquarterNotFoundException.class)
    public WorkOrderErrorResponse handleHeadquarter(HeadquarterNotFoundException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.HEADQUARTER_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ServiceAreaNotFoundException.class)
    public WorkOrderErrorResponse handleServiceArea(ServiceAreaNotFoundException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.SERVICE_AREA_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ClientEquipmentNotFoundException.class)
    public WorkOrderErrorResponse handleClientEquipment(ClientEquipmentNotFoundException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.CLIENT_EQUIPMENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PersonNotFoundException.class)
    public WorkOrderErrorResponse handlePerson(PersonNotFoundException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.PERSON_NOT_FOUND, List.of(ex.getMessage()));
    }

    /**
     * El estado de la orden no admite la operación: 409 y no 400.
     *
     * <p>Los datos recibidos son válidos y no falta ninguno; lo que choca es el momento — arrancar
     * una orden ya ejecutada, o tocar el alcance de una cancelada. Compartir el código de "datos
     * inválidos" impediría al llamante distinguir "lo has escrito mal" de "ahora no se puede".
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public WorkOrderErrorResponse handleState(IllegalStateException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.WORK_ORDER_STATE_CONFLICT, List.of(ex.getMessage()));
    }

    /** Las reglas de los agregados y de los servicios llegan como esto. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public WorkOrderErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.INVALID_WORK_ORDER_DATA, List.of(ex.getMessage()));
    }
}
