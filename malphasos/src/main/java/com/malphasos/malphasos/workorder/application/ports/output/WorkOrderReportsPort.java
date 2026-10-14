package com.malphasos.malphasos.workorder.application.ports.output;

import java.util.Set;
import java.util.UUID;

/**
 * Qué equipos de una orden tienen ya su reporte cerrado.
 *
 * <p>Lo necesita la regla de ejecutar (2026-10-14): una orden no se da por ejecutada mientras le
 * quede un equipo sin reporte o con el reporte en borrador. Los reportes son de {@code report}, y ese
 * módulo depende de este y no al revés, así que <b>la pregunta la declara este módulo y la contesta
 * aquel</b>: es la misma inversión que {@code InterventionRecordingPort} entre {@code report} y
 * {@code equipment}.
 */
public interface WorkOrderReportsPort {

    /** Los equipos de la orden cuyo reporte vigente está cerrado. Los retirados no cuentan. */
    Set<UUID> equipmentWithFinishedReport(UUID idOrdenTrabajo);
}
