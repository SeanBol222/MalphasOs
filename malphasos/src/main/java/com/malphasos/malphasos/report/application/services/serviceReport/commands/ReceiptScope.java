package com.malphasos.malphasos.report.application.services.serviceReport.commands;

/**
 * A cuántos reportes de la orden va una firma de quien recibe (RF-21, 2026-10-30). Quien recibe no es un
 * dato de la orden ni del área —en el periodo siguiente puede ser otra persona—, así que la firma se queda
 * en cada reporte; esto solo dice cuántos se rellenan de golpe.
 */
public enum ReceiptScope {

    /** Solo el reporte pedido. */
    REPORTE,

    /** Los de la orden cuyo equipo está en la misma área que el del reporte pedido. */
    AREA,

    /** Todos los de la orden, que es toda la sede: una orden es de una sola. */
    ORDEN
}
