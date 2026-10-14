package com.malphasos.malphasos.equipment.domain.exception;

/** Otro equipo patrón vigente ya tiene esa serie: la serie es lo que identifica al aparato. */
public class ReferenceStandardSerialTakenException extends RuntimeException {

    public ReferenceStandardSerialTakenException(String serie, String nombre) {
        super("Ya hay un equipo patron vigente con la serie " + serie + ": " + nombre);
    }
}
