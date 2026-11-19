package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/** Ese tipo de equipo no tiene una verificación con ese identificador. */
public class VerificationNotFoundException extends RuntimeException {

    public VerificationNotFoundException(UUID idTipoEquipo, UUID idVerificacion) {
        super("El tipo de equipo " + idTipoEquipo + " no tiene la verificacion " + idVerificacion);
    }
}
