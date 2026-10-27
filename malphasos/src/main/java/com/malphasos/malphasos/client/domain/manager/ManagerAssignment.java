package com.malphasos.malphasos.client.domain.manager;

import java.util.UUID;

/**
 * Una sede o un área de la que una persona está a cargo (2026-10-27, `V41`).
 *
 * <p>Es un valor, no una entidad: no tiene identidad propia, y en la base su llave es el par
 * (persona, sede) o (persona, área). Retirarla no la borra, la deja inactiva, y volver a darla la
 * reactiva en la misma fila.
 */
public record ManagerAssignment(ManagerType tipo, UUID idAsignacion, boolean estadoActivo) {

    public ManagerAssignment {
        if (tipo == null || idAsignacion == null) {
            throw new IllegalArgumentException("Una asignacion necesita tipo y destino");
        }
    }

    /** Si apunta a esa sede o a esa área, esté activa o no. */
    boolean es(ManagerType otroTipo, UUID otraAsignacion) {
        return tipo == otroTipo && idAsignacion.equals(otraAsignacion);
    }

    ManagerAssignment conEstado(boolean activa) {
        return new ManagerAssignment(tipo, idAsignacion, activa);
    }
}
