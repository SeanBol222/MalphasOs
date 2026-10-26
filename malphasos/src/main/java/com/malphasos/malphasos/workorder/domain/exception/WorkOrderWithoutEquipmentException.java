package com.malphasos.malphasos.workorder.domain.exception;

/**
 * La orden no se puede empezar: no tiene equipos (2026-10-26).
 *
 * <p>Gemela de {@link WorkOrderWithoutEngineerException}, y por la misma razón: con el código
 * genérico de estado, la pantalla no podía decir qué hacer.
 */
public class WorkOrderWithoutEquipmentException extends IllegalStateException {

    public WorkOrderWithoutEquipmentException() {
        super("No se puede empezar una orden sin equipos");
    }
}
