package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/**
 * Quien recibe el servicio firma el recibido (V39). Con un {@code alcance} mayor que el reporte, la misma
 * firma vale para los reportes del área o de la orden que aún no tengan, abiertos o cerrados (RF-21).
 *
 * @param firma los bytes del PNG que dibujó
 */
public record SignReceiptCommand(
        UUID id, String nombre, String cedula, String cargo, byte[] firma, ReceiptScope alcance) {

    public SignReceiptCommand {
        if (alcance == null) {
            alcance = ReceiptScope.REPORTE;
        }
    }
}
