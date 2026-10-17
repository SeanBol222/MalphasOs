package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/**
 * Quien recibe el servicio firma el recibido (V39). Con {@code aplicarALaOrden} la misma firma vale para
 * los reportes de esa orden que aún no tengan, abiertos o cerrados (RF-21).
 *
 * @param firma los bytes del PNG que dibujó
 */
public record SignReceiptCommand(
        UUID id, String nombre, String cedula, String cargo, byte[] firma, boolean aplicarALaOrden) {
}
