package com.malphasos.malphasos.report.application.model;

import java.util.UUID;

/**
 * Cuántos equipos de una orden tienen su reporte cerrado y cuántos lo tienen en borrador (2026-10-16).
 *
 * <p>Es lo que el listado de órdenes enseña en cada fila —«3 de 5 cerrados»—, y por eso se compila
 * aquí, en una consulta, y no reporte a reporte. Cuenta equipos y no reportes, y solo los vivos: un
 * reporte retirado deja al equipo sin reporte, igual que dice la ficha de la orden y la regla que
 * impide ejecutarla.
 */
public record WorkOrderReportProgress(UUID idOrdenTrabajo, long cerrados, long abiertos) {
}
