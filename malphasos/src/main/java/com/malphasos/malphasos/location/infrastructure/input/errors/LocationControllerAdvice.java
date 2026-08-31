package com.malphasos.malphasos.location.infrastructure.input.errors;

import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import com.malphasos.malphasos.location.domain.exception.CountryNotFoundException;
import com.malphasos.malphasos.location.infrastructure.input.rest.CityRestAdapter;
import com.malphasos.malphasos.location.infrastructure.input.rest.CountryRestAdapter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de este módulo al contrato de error del API.
 *
 * <p>Se limita con {@code assignableTypes} a los controladores de ubicaciones, para no interceptar
 * lo que el manejador transversal trata mejor. El advice del original capturaba {@code Exception} y
 * devolvía su mensaje al cliente.
 *
 * <p>Las violaciones de integridad —un código ISO repetido, dos ciudades con el mismo nombre en el
 * mismo país— no se manejan aquí: el manejador transversal ya las traduce a 409.
 */
@RestControllerAdvice(assignableTypes = {CountryRestAdapter.class, CityRestAdapter.class})
public class LocationControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CountryNotFoundException.class)
    public LocationErrorResponse handleCountryNotFound(CountryNotFoundException ex) {
        return LocationErrorResponse.of(LocationErrorCatalog.COUNTRY_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CityNotFoundException.class)
    public LocationErrorResponse handleCityNotFound(CityNotFoundException ex) {
        return LocationErrorResponse.of(LocationErrorCatalog.CITY_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** Las reglas del agregado —código con formato inválido, nombre vacío— llegan como esto. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public LocationErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return LocationErrorResponse.of(
                LocationErrorCatalog.INVALID_LOCATION_DATA, List.of(ex.getMessage()));
    }
}
