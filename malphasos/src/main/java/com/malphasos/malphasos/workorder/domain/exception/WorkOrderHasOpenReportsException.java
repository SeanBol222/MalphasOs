package com.malphasos.malphasos.workorder.domain.exception;

import java.util.UUID;

/**
 * La orden no se puede dar por ejecutada: le quedan equipos sin reporte cerrado (2026-10-14).
 *
 * <p>Es un conflicto de estado como cualquier otro, pero con código propio: la pantalla tiene que
 * poder decir «faltan dos reportes» y no «el estado no lo permite», que no dice qué hacer.
 */
public class WorkOrderHasOpenReportsException extends RuntimeException {

    private final int pendientes;

    public WorkOrderHasOpenReportsException(UUID idOrden, int pendientes) {
        super("La orden " + idOrden + " tiene " + pendientes
                + (pendientes == 1 ? " equipo" : " equipos") + " sin reporte cerrado");
        this.pendientes = pendientes;
    }

    public int getPendientes() {
        return pendientes;
    }
}
