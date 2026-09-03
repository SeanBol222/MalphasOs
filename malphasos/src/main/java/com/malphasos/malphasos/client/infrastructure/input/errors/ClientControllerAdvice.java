package com.malphasos.malphasos.client.infrastructure.input.errors;

import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ManagerNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.client.infrastructure.input.rest.ClientRestAdapter;
import com.malphasos.malphasos.client.infrastructure.input.rest.HeadquarterRestAdapter;
import com.malphasos.malphasos.client.infrastructure.input.rest.ManagerRestAdapter;
import com.malphasos.malphasos.client.infrastructure.input.rest.ServiceAreaRestAdapter;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de este módulo al contrato de error del API.
 *
 * <p>Maneja también las de los módulos que este consulta —una ciudad o una persona inexistentes—,
 * porque llegan por sus controladores: sin esto, escaparían al manejador transversal y volverían
 * como un 500. Se conserva el código de origen en el detalle para que el cliente sepa cuál de las
 * referencias falló.
 */
@RestControllerAdvice(
        assignableTypes = {
            ClientRestAdapter.class,
            HeadquarterRestAdapter.class,
            ServiceAreaRestAdapter.class,
            ManagerRestAdapter.class
        })
public class ClientControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ClientNotFoundException.class)
    public ClientErrorResponse handleClientNotFound(ClientNotFoundException ex) {
        return ClientErrorResponse.of(ClientErrorCatalog.CLIENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(HeadquarterNotFoundException.class)
    public ClientErrorResponse handleHeadquarterNotFound(HeadquarterNotFoundException ex) {
        return ClientErrorResponse.of(
                ClientErrorCatalog.HEADQUARTER_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ServiceAreaNotFoundException.class)
    public ClientErrorResponse handleServiceAreaNotFound(ServiceAreaNotFoundException ex) {
        return ClientErrorResponse.of(
                ClientErrorCatalog.SERVICE_AREA_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ManagerNotFoundException.class)
    public ClientErrorResponse handleManagerNotFound(ManagerNotFoundException ex) {
        return ClientErrorResponse.of(ClientErrorCatalog.MANAGER_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** Referencias hacia otros módulos que no existen. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({CityNotFoundException.class, PersonNotFoundException.class})
    public ClientErrorResponse handleReferenciaInexistente(RuntimeException ex) {
        return ClientErrorResponse.of(
                ClientErrorCatalog.INVALID_CLIENT_DATA, List.of(ex.getMessage()));
    }

    /** Las reglas de los agregados y de los servicios llegan como esto. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ClientErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return ClientErrorResponse.of(
                ClientErrorCatalog.INVALID_CLIENT_DATA, List.of(ex.getMessage()));
    }
}
