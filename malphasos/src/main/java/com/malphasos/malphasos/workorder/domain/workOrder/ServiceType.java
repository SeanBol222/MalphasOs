package com.malphasos.malphasos.workorder.domain.workOrder;

/**
 * Qué clase de servicio se presta en una orden.
 *
 * <p>No existía en el sistema original, ni como columna ni como código: sale del vocabulario que la
 * especificación de requisitos ya emplea al describir el negocio.
 */
public enum ServiceType {

    /** Revisión programada para que el equipo no falle. Es el núcleo del negocio. */
    PREVENTIVO,

    /** Intervención sobre un equipo que ya falló. */
    CORRECTIVO,

    /** Ajuste del equipo contra un patrón de referencia. */
    CALIBRACION
}
