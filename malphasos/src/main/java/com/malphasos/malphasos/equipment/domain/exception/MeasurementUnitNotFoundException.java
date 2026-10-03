package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe una unidad de medida con ese identificador. */
public class MeasurementUnitNotFoundException extends RuntimeException {

    public MeasurementUnitNotFoundException(UUID id) {
        super("No existe una unidad de medida con el identificador " + id);
    }
}
