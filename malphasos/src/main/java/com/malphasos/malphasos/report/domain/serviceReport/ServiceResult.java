package com.malphasos.malphasos.report.domain.serviceReport;

/**
 * Cómo queda el equipo después de la intervención.
 *
 * <p>⚠️ <b>Este vocabulario no sale de la ERS.</b> RF-15 enumera cinco campos del reporte y uno es
 * «resultado», pero no dice qué valores admite —a diferencia de las periodicidades y los tipos de
 * servicio de la orden de trabajo, que sí estaban escritos en el documento—. Estos tres son una
 * propuesta: lo que un mantenimiento puede concluir sobre un equipo.
 *
 * <p>Va como catálogo cerrado y no como texto libre porque de este dato cuelgan el historial de la
 * hoja de vida (RF-26) y las alertas (RF-40), y un texto libre no se agrupa ni dispara nada.
 */
public enum ServiceResult {

    /** El equipo queda en servicio, sin restricciones. */
    OPERATIVO,

    /** El equipo queda en servicio, pero con alguna limitación que las observaciones detallan. */
    OPERATIVO_CON_RESTRICCIONES,

    /** El equipo queda fuera de servicio. */
    FUERA_DE_SERVICIO
}
