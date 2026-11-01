package com.malphasos.malphasos.workorder.domain.exception;

import java.util.UUID;

/** Aprobar desde la propia cuenta usa la firma guardada, y esta persona no ha guardado ninguna. */
public class NoSavedSignatureException extends RuntimeException {

    public NoSavedSignatureException(UUID idPersona) {
        super("La persona " + idPersona + " no ha guardado su firma: guárdela antes de aprobar desde su cuenta");
    }
}
