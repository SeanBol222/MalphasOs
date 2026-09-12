package com.malphasos.malphasos.equipment.infrastructure.input.errors;

import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.BrandNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.CrossClientRelocationException;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ManufacturerNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ModelNotFoundException;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.BrandRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ClientEquipmentRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentTypeRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ManufacturerRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ModelRestAdapter;
import com.malphasos.malphasos.location.domain.exception.CountryNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de este módulo al contrato de error del API.
 *
 * <p>Maneja también las de los módulos que este consulta —un país o un área de servicio
 * inexistentes—, porque llegan por sus controladores y de otro modo escaparían al manejador
 * transversal como un 500. Cada una lleva su propio código: el 404 dice que algo no existe, y el
 * código dice qué.
 */
@RestControllerAdvice(
        assignableTypes = {
            ManufacturerRestAdapter.class,
            BrandRestAdapter.class,
            EquipmentTypeRestAdapter.class,
            EquipmentRestAdapter.class,
            ModelRestAdapter.class,
            ClientEquipmentRestAdapter.class
        })
public class EquipmentControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ManufacturerNotFoundException.class)
    public EquipmentErrorResponse handleManufacturer(ManufacturerNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.MANUFACTURER_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(BrandNotFoundException.class)
    public EquipmentErrorResponse handleBrand(BrandNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.BRAND_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(EquipmentTypeNotFoundException.class)
    public EquipmentErrorResponse handleType(EquipmentTypeNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.EQUIPMENT_TYPE_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(EquipmentNotFoundException.class)
    public EquipmentErrorResponse handleEquipment(EquipmentNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.EQUIPMENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ModelNotFoundException.class)
    public EquipmentErrorResponse handleModel(ModelNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.MODEL_NOT_FOUND, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ClientEquipmentNotFoundException.class)
    public EquipmentErrorResponse handleUnit(ClientEquipmentNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.CLIENT_EQUIPMENT_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** El país de un fabricante, que vive en el módulo de ubicación. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CountryNotFoundException.class)
    public EquipmentErrorResponse handleCountry(CountryNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.COUNTRY_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** El área donde se instala una unidad, que vive en el módulo de clientes. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ServiceAreaNotFoundException.class)
    public EquipmentErrorResponse handleServiceArea(ServiceAreaNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.SERVICE_AREA_NOT_FOUND, List.of(ex.getMessage()));
    }

    /**
     * Un traslado que cruza de cliente.
     *
     * <p>409 y no 400: los datos que llegaron son válidos —el área existe y está abierta— y no es
     * que algo no exista. Lo que ocurre es que la operación choca con el estado actual de la
     * unidad, que ya pertenece a otro cliente. Lleva código propio para que quien llama pueda
     * distinguirlo de un dato mal formado sin leer el mensaje.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(CrossClientRelocationException.class)
    public EquipmentErrorResponse handleCrossClientRelocation(CrossClientRelocationException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.CROSS_CLIENT_RELOCATION, List.of(ex.getMessage()));
    }

    /** Las reglas de los agregados y de los servicios llegan como esto. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public EquipmentErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.INVALID_EQUIPMENT_DATA, List.of(ex.getMessage()));
    }
}
