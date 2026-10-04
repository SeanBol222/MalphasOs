package com.malphasos.malphasos.equipment.domain.intervention;

/**
 * En qué estado quedó el equipo tras el servicio.
 *
 * <p>Repite los valores de {@code ServiceResult} del módulo de reportes por la misma razón que
 * {@link InterventionType} repite los del de órdenes: {@code report} importa {@code equipment}, así
 * que depender de su enum invertiría el grafo.
 *
 * <p><b>Y arrastra una advertencia que viene de su origen</b>: este vocabulario <b>no sale de la
 * ERS</b>. RF-15 enumera «resultado» entre los cinco campos del reporte y no dice qué valores admite,
 * al contrario que las periodicidades y los tipos de servicio, que sí estaban escritos. Es una
 * propuesta pendiente de decisión, y ahora la arrastran dos tablas en vez de una: cambiar la lista
 * cuesta dos migraciones.
 */
public enum InterventionResult {
    OPERATIVO,
    OPERATIVO_CON_RESTRICCIONES,
    FUERA_DE_SERVICIO
}
