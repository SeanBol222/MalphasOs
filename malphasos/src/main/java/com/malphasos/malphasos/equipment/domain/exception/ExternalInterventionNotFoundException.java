package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** No existe esa intervención externa, o no es de ese equipo. */
public class ExternalInterventionNotFoundException extends RuntimeException {

    public ExternalInterventionNotFoundException(UUID id) {
        super("No existe la intervencion externa " + id);
    }
}
