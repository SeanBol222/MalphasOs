package com.malphasos.malphasos.report.domain.serviceReport;

/**
 * En qué punto de su vida está un reporte de servicio.
 *
 * <p>Solo dos estados, y es deliberado: un reporte se abre vacío al llegar al equipo, se llena en
 * campo y se cierra. Firmarlo y exportarlo a PDF son RF-21 y RF-17, que dependen de la firma digital
 * y no existen todavía; cuando lleguen añadirán su propio estado y su propio {@code CHECK}.
 *
 * <p><b>No se vuelve atrás.</b> Un reporte cerrado es lo que se entregó al cliente y de él cuelgan el
 * historial de la hoja de vida (RF-26) y las alertas de calibración (RF-40): reabrirlo reescribiría un
 * hecho. Si hay que corregir uno, se retira y se abre otro, que es justo lo que permite el índice
 * único parcial de {@code reporte_servicio}.
 */
public enum ReportState {

    /** Abierto y en blanco, o a medio llenar. Es el estado en que nace todo reporte. */
    BORRADOR,

    /** Cerrado. Desde aquí el reporte ya no cambia. */
    FINALIZADO;

    /** Si el reporte ya está cerrado y no admite más cambios. */
    public boolean esFinal() {
        return this == FINALIZADO;
    }
}
