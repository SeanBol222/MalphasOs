package com.malphasos.malphasos.company.infrastructure.input.errors;

import com.malphasos.malphasos.company.domain.exception.ServiceCompanyNotConfiguredException;
import com.malphasos.malphasos.company.infrastructure.input.rest.ServiceCompanyRestAdapter;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce las excepciones de este módulo al contrato de error del API. */
@RestControllerAdvice(assignableTypes = ServiceCompanyRestAdapter.class)
public class CompanyControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ServiceCompanyNotConfiguredException.class)
    public CompanyErrorResponse handleNotConfigured(ServiceCompanyNotConfiguredException ex) {
        return CompanyErrorResponse.of(CompanyErrorCatalog.COMPANY_NOT_CONFIGURED, List.of(ex.getMessage()));
    }

    /** La ciudad de la empresa es del catálogo (V44): una que no existe es un 404 con su propio código. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CityNotFoundException.class)
    public CompanyErrorResponse handleCity(CityNotFoundException ex) {
        return CompanyErrorResponse.of(CompanyErrorCatalog.CITY_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** Las reglas del agregado llegan como esto: un nombre en blanco, un logo que no es imagen. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public CompanyErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return CompanyErrorResponse.of(CompanyErrorCatalog.INVALID_COMPANY_DATA, List.of(ex.getMessage()));
    }
}
