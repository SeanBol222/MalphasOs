package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/**
 * Quien recibe el servicio firma el recibido (V39). Con un {@code alcance} mayor que el reporte, la misma
 * firma vale para los reportes del área o de la orden que aún no tengan, abiertos o cerrados (RF-21).
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
}
