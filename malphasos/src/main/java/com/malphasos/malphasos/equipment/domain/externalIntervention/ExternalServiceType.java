package com.malphasos.malphasos.equipment.domain.externalIntervention;

/**
 * Qué clase de servicio le hizo al equipo otra empresa (V61, 2026-11-23): los tres de un reporte, más
 * {@link #OTRO} —una inspección, una actualización de software, un traslado—, que fuera de MalphasOS no
 * se puede descartar. El esquema repite la lista, y una prueba de esquema la fija.
 */
public enum ExternalServiceType {
    PREVENTIVO,
    CORRECTIVO,
    CALIBRACION,
    OTRO
}
