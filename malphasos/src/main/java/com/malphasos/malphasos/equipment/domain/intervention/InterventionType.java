package com.malphasos.malphasos.equipment.domain.intervention;

/**
 * Qué clase de servicio se le prestó al equipo.
 *
 * <p>Repite los valores de {@code ServiceType} del módulo de órdenes de trabajo, de donde se copian,
 * y es una repetición deliberada: la intervención es una copia congelada, de modo que su vocabulario
 * tiene que poder leerse sin depender de un módulo que está <b>aguas abajo</b> en el grafo de
 * dependencias —{@code work-order} importa {@code equipment}, no al contrario—. Reutilizar aquel
 * enum habría invertido esa dirección por un tipo de tres valores.
 *
 * <p>El esquema repite la lista por la misma razón, y una prueba de esquema fija que admita
 * exactamente estos tres.
 */
public enum InterventionType {
    PREVENTIVO,
    CORRECTIVO,
    CALIBRACION
}
