package com.malphasos.malphasos.workorder.infrastructure.input.errors;

import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderHasOpenReportsException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderWithoutEngineerException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderWithoutEquipmentException;
import com.malphasos.malphasos.workorder.infrastructure.input.rest.WorkOrderApprovalRestAdapter;
import com.malphasos.malphasos.workorder.infrastructure.input.rest.WorkOrderRestAdapter;
import com.malphasos.malphasos.workorder.domain.exception.NoSavedSignatureException;
import com.malphasos.malphasos.workorder.domain.exception.NotSupervisorOfClientException;
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
@RestControllerAdvice(assignableTypes = {WorkOrderRestAdapter.class, WorkOrderApprovalRestAdapter.class})
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
    /** Quedan equipos sin reporte cerrado: 409, con cuántos en los detalles. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(WorkOrderHasOpenReportsException.class)
    public WorkOrderErrorResponse handleOpenReports(WorkOrderHasOpenReportsException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.WORK_ORDER_HAS_OPEN_REPORTS, List.of(ex.getMessage()));
    }

    /** Sin ingeniero no se empieza: 409, con código propio para que la pantalla diga qué falta. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(WorkOrderWithoutEngineerException.class)
    public WorkOrderErrorResponse handleWithoutEngineer(WorkOrderWithoutEngineerException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.WORK_ORDER_WITHOUT_ENGINEER, List.of(ex.getMessage()));
    }

    /** Sin equipos tampoco. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(WorkOrderWithoutEquipmentException.class)
    public WorkOrderErrorResponse handleWithoutEquipment(WorkOrderWithoutEquipmentException ex) {
        return WorkOrderErrorResponse.of(
                WorkOrderErrorCatalog.WORK_ORDER_WITHOUT_EQUIPMENT, List.of(ex.getMessage()));
    }

    /** Solo aprueba un supervisor activo del cliente de la orden (V54). */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(NotSupervisorOfClientException.class)
    public WorkOrderErrorResponse handleNotSupervisor(NotSupervisorOfClientException ex) {
        return WorkOrderErrorResponse.of(WorkOrderErrorCatalog.NOT_SUPERVISOR_OF_CLIENT, List.of(ex.getMessage()));
    }

    /** Aprobar desde la cuenta usa la firma guardada: sin ella, 409 con código propio para que se guarde. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(NoSavedSignatureException.class)
    public WorkOrderErrorResponse handleNoSavedSignature(NoSavedSignatureException ex) {
        return WorkOrderErrorResponse.of(WorkOrderErrorCatalog.NO_SAVED_SIGNATURE, List.of(ex.getMessage()));
    }

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
