package com.malphasos.malphasos.equipment.domain.exception;

/** Otro equipo patrón vigente ya tiene ese código interno: es como lo nombra el inventario de la empresa. */
public class ReferenceStandardInternalCodeTakenException extends RuntimeException {

    public ReferenceStandardInternalCodeTakenException(String codigo, String nombre) {
        super("Ya hay un equipo patron vigente con el codigo interno " + codigo + ": " + nombre);
    }
}
