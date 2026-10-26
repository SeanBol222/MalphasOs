package com.malphasos.malphasos.workorder.domain.exception;

/**
 * La orden no se puede empezar: no tiene ingeniero asignado (2026-10-26).
 *
 * <p>Es un conflicto de estado —por eso hereda de {@link IllegalStateException}— pero con código
 * propio: compartía el de «el estado no lo permite», y quien pulsaba «Iniciar trabajo» en una orden
 * creada leía que una orden se inicia estando creada, sin enterarse de que lo que faltaba era el
 * ingeniero.
 */
public class WorkOrderWithoutEngineerException extends IllegalStateException {

    public WorkOrderWithoutEngineerException() {
        super("No se puede empezar una orden sin ingeniero asignado");
    }
}
