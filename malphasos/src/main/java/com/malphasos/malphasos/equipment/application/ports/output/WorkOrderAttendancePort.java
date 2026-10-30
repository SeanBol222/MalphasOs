package com.malphasos.malphasos.equipment.application.ports.output;

import java.util.UUID;

/**
 * Si una unidad está en una orden de trabajo que todavía no se ha ejecutado (2026-10-28, {@code V50}).
 *
 * <p>Lo necesita el traslado: mientras la orden vive, el área de sus equipos no cambia. Que un equipo esté
 * en una orden lo dice su reporte activo en ella, y los reportes son de {@code report}, que depende de este
 * módulo y no al revés: <b>la pregunta la declara este módulo y la contesta aquel</b>, como
 * {@code WorkOrderReportsPort} entre {@code work-order} y {@code report}.
 */
public interface WorkOrderAttendancePort {

    /** Si la unidad tiene un reporte activo en una orden viva que no se ha ejecutado. */
    boolean inUnexecutedWorkOrder(UUID idEquipoCliente);
}
