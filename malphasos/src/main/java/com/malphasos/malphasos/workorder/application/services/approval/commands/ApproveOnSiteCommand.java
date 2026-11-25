package com.malphasos.malphasos.workorder.application.services.approval.commands;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Aprobación en sitio: el supervisor dibuja su firma en la sesión de otro.
 *
 * <p>Un record compara un arreglo por referencia: {@code equals} y {@code hashCode} comparan los bytes de
 * la firma, y {@code toString} dice cuántos son en vez de volcarlos.
 *
 * @param firma los bytes del PNG que dibujó
 * @param idOperador quien tiene la sesión, por el {@code sub} de su token; nunca del cuerpo
 */
public record ApproveOnSiteCommand(UUID idOrden, UUID idSupervisor, byte[] firma, UUID idOperador) {

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ApproveOnSiteCommand o
                && Objects.equals(idOrden, o.idOrden)
                && Objects.equals(idSupervisor, o.idSupervisor)
                && Arrays.equals(firma, o.firma)
                && Objects.equals(idOperador, o.idOperador);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(idOrden, idSupervisor, idOperador) + Arrays.hashCode(firma);
    }

    @Override
    public String toString() {
        return "ApproveOnSiteCommand[idOrden=" + idOrden + ", idSupervisor=" + idSupervisor + ", firma="
                + (firma == null ? null : firma.length + " bytes") + ", idOperador=" + idOperador + "]";
    }
}
