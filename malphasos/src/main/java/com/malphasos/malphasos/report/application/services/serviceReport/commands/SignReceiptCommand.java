package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Quien recibe el servicio firma el recibido (V39). Con un {@code alcance} mayor que el reporte, la misma
 * firma vale para los reportes del área o de la orden que aún no tengan, abiertos o cerrados (RF-21).
 *
 * <p>Un record compara un arreglo por referencia: {@code equals} y {@code hashCode} comparan los bytes de
 * la firma, y {@code toString} dice cuántos son en vez de volcarlos.
 *
 * @param firma los bytes del PNG que dibujó
 * @param idPersonaRecibe si quien recibe es una persona registrada —el supervisor del cliente, V55—; entonces
 *     nombre, cédula y cargo van vacíos, porque se leen de su ficha
 */
public record SignReceiptCommand(
        UUID id, String nombre, String cedula, String cargo, byte[] firma, ReceiptScope alcance,
        UUID idPersonaRecibe) {

    public SignReceiptCommand {
        if (alcance == null) {
            alcance = ReceiptScope.REPORTE;
        }
    }

    /** Quien recibe, escrito en texto: lo de siempre, antes de V55. */
    public SignReceiptCommand(
            UUID id, String nombre, String cedula, String cargo, byte[] firma, ReceiptScope alcance) {
        this(id, nombre, cedula, cargo, firma, alcance, null);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof SignReceiptCommand o
                && Objects.equals(id, o.id)
                && Objects.equals(nombre, o.nombre)
                && Objects.equals(cedula, o.cedula)
                && Objects.equals(cargo, o.cargo)
                && Arrays.equals(firma, o.firma)
                && alcance == o.alcance
                && Objects.equals(idPersonaRecibe, o.idPersonaRecibe);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(id, nombre, cedula, cargo, alcance, idPersonaRecibe) + Arrays.hashCode(firma);
    }

    @Override
    public String toString() {
        return "SignReceiptCommand[id=" + id + ", nombre=" + nombre + ", cedula=" + cedula + ", cargo=" + cargo
                + ", firma=" + (firma == null ? null : firma.length + " bytes") + ", alcance=" + alcance
                + ", idPersonaRecibe=" + idPersonaRecibe + "]";
    }
}
