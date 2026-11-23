package com.malphasos.malphasos.equipment.infrastructure.input.errors;

import com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfUnavailableException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ExternalInterventionNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.BrandNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolInUseException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNameTakenException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardInternalCodeTakenException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardSerialTakenException;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.CrossClientRelocationException;
import com.malphasos.malphasos.equipment.domain.exception.RelocationDuringWorkOrderException;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.MagnitudeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ManufacturerNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.MeasurementUnitNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ModelNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.UnitOutsideMagnitudeException;
import com.malphasos.malphasos.equipment.domain.exception.VerificationNotFoundException;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.BrandRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ClientEquipmentRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentOverviewRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentPhotoRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ExternalInterventionRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentTypeProtocolRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentTypeRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.EquipmentTypeStandardsRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.InterventionRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.LifeSheetRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ManufacturerRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.MetrologyCatalogRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ModelRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ProtocolRestAdapter;
import com.malphasos.malphasos.equipment.infrastructure.input.rest.ReferenceStandardRestAdapter;
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
            ClientEquipmentRestAdapter.class,
            MetrologyCatalogRestAdapter.class,
            ProtocolRestAdapter.class,
            EquipmentTypeProtocolRestAdapter.class,
            EquipmentTypeStandardsRestAdapter.class,
            ReferenceStandardRestAdapter.class,
            // Las tres que faltaban: hasta el 2026-10-18, un equipo que no existe respondía 500 en su hoja
            // de vida y en su historial, porque sus errores no llegaban a este manejador.
            LifeSheetRestAdapter.class,
            InterventionRestAdapter.class,
            EquipmentOverviewRestAdapter.class,
            EquipmentPhotoRestAdapter.class,
            ExternalInterventionRestAdapter.class
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

    /** Una magnitud que no existe en el catálogo metrológico. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(MagnitudeNotFoundException.class)
    public EquipmentErrorResponse handleMagnitude(MagnitudeNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.MAGNITUDE_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** Una unidad de medida que no existe en el catálogo metrológico. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(MeasurementUnitNotFoundException.class)
    public EquipmentErrorResponse handleMeasurementUnit(MeasurementUnitNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.MEASUREMENT_UNIT_NOT_FOUND, List.of(ex.getMessage()));
    }

    /**
     * Una unidad que existe pero no es de la magnitud declarada.
     *
     * <p>409 y no 400 ni 404, por lo mismo que el traslado entre clientes: las dos filas existen y los
     * dos identificadores son correctos, lo que choca es la combinación. Con 404 el cliente del API
     * buscaría una unidad que sí está, y con 400 creería que el identificador está mal escrito.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(UnitOutsideMagnitudeException.class)
    public EquipmentErrorResponse handleUnitOutsideMagnitude(UnitOutsideMagnitudeException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.UNIT_OUTSIDE_MAGNITUDE, List.of(ex.getMessage()));
    }

    /**
     * Un traslado que cruza de cliente.
     *
     * <p>409 y no 400: los datos que llegaron son válidos —el área existe y está abierta— y no es
     * que algo no exista. Lo que ocurre es que la operación choca con el estado actual de la
     * unidad, que ya pertenece a otro cliente. Lleva código propio para que quien llama pueda
     * distinguirlo de un dato mal formado sin leer el mensaje.
     */
    /** 409 por lo mismo que el traslado entre clientes: la unidad está en una orden sin ejecutar (V50). */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(RelocationDuringWorkOrderException.class)
    public EquipmentErrorResponse handleRelocationDuringWorkOrder(RelocationDuringWorkOrderException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.RELOCATION_DURING_WORK_ORDER, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(CrossClientRelocationException.class)
    public EquipmentErrorResponse handleCrossClientRelocation(CrossClientRelocationException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.CROSS_CLIENT_RELOCATION, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ProtocolNotFoundException.class)
    public EquipmentErrorResponse handleProtocol(ProtocolNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.PROTOCOL_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** Otro protocolo vigente ya tiene ese nombre, sin contar mayúsculas ni tildes. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ProtocolNameTakenException.class)
    public EquipmentErrorResponse handleProtocolNameTaken(ProtocolNameTakenException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.PROTOCOL_NAME_TAKEN, List.of(ex.getMessage()));
    }

    /** Un protocolo que se quiere retirar mientras lo usan tipos de equipo vigentes. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ProtocolInUseException.class)
    public EquipmentErrorResponse handleProtocolInUse(ProtocolInUseException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.PROTOCOL_IN_USE, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ReferenceStandardNotFoundException.class)
    public EquipmentErrorResponse handleReferenceStandard(ReferenceStandardNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.REFERENCE_STANDARD_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** La verificación no existe en ese tipo de equipo, aunque el tipo sí. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(VerificationNotFoundException.class)
    public EquipmentErrorResponse handleVerification(VerificationNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.VERIFICATION_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** La intervención externa no existe, o no es de ese equipo. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ExternalInterventionNotFoundException.class)
    public EquipmentErrorResponse handleExternalIntervention(ExternalInterventionNotFoundException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.EXTERNAL_INTERVENTION_NOT_FOUND, List.of(ex.getMessage()));
    }

    /** Otro equipo patrón vigente ya tiene esa serie. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ReferenceStandardSerialTakenException.class)
    public EquipmentErrorResponse handleReferenceStandardSerialTaken(ReferenceStandardSerialTakenException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.REFERENCE_STANDARD_SERIAL_TAKEN, List.of(ex.getMessage()));
    }

    /** Otro equipo patrón vigente ya tiene ese código interno. */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ReferenceStandardInternalCodeTakenException.class)
    public EquipmentErrorResponse handleReferenceStandardInternalCodeTaken(
            ReferenceStandardInternalCodeTakenException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.REFERENCE_STANDARD_INTERNAL_CODE_TAKEN, List.of(ex.getMessage()));
    }

    /** Gotenberg no respondió: no es culpa de lo pedido, y se puede volver a intentar. */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(LifeSheetPdfUnavailableException.class)
    public EquipmentErrorResponse handleLifeSheetPdfUnavailable(LifeSheetPdfUnavailableException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.LIFE_SHEET_PDF_UNAVAILABLE, List.of(ex.getMessage()));
    }

    /** Las reglas de los agregados y de los servicios llegan como esto. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public EquipmentErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return EquipmentErrorResponse.of(
                EquipmentErrorCatalog.INVALID_EQUIPMENT_DATA, List.of(ex.getMessage()));
    }
}
