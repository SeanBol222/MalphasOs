package com.malphasos.malphasos.report.domain.serviceReport;

import java.util.UUID;

/**
 * Un patrón usado en una verificación de un reporte (V30, 2026-10-16).
 *
 * <p>Decidido por el usuario: una verificación se mide a veces contra varios patrones —una balanza con
 * tres juegos de pesas— y un termohigrómetro verifica temperatura y humedad contra patrones distintos,
 * así que el patrón deja de ser uno por reporte y pasa a ser una lista por verificación.
 *
 * <p><b>Guarda qué patrón, no cómo era</b>, desde {@code V51} (2026-10-28): la ficha se lee del catálogo
 * mientras el reporte es borrador, y al cerrarse queda en su documento. Hasta entonces la copiaba aquí.
 *
 * <p>No es un agregado: vive y muere con su reporte, como las lecturas. <b>Quitarlo lo retira y volver
 * a ponerlo lo reactiva</b>, con el mismo identificador: así nunca compiten dos filas activas por el
 * mismo sitio, que es la trampa de Hibernate con los índices únicos parciales.
 *
 * <p><b>Dice de dónde vino</b> desde {@code V60} (2026-11-19): {@code desdeOrden} si lo puso la orden, que
 * reparte sus patrones por tipo de equipo entre sus reportes. Cambiar los de la orden pone y quita los
 * suyos, y nunca los que el ingeniero puso a mano.
 */
public record VerificationStandard(
        UUID id, UUID idVerificacion, UUID idEquipoPatron, boolean desdeOrden, boolean estadoActivo) {

    /** Uno puesto a mano. */
    public VerificationStandard(UUID id, UUID idVerificacion, UUID idEquipoPatron, boolean estadoActivo) {
        this(id, idVerificacion, idEquipoPatron, false, estadoActivo);
    }

    public VerificationStandard {
        if (id == null || idVerificacion == null || idEquipoPatron == null) {
            throw new IllegalArgumentException(
                    "Un patron de una verificacion necesita su identificador, su verificacion y el patron");
        }
    }

    static VerificationStandard of(UUID idVerificacion, UUID idEquipoPatron, boolean desdeOrden) {
        return new VerificationStandard(UUID.randomUUID(), idVerificacion, idEquipoPatron, desdeOrden, true);
    }

    /** Si es este patrón en esta verificación, esté activo o retirado. */
    boolean ocupa(UUID verificacion, UUID idPatron) {
        return idVerificacion.equals(verificacion) && idEquipoPatron.equals(idPatron);
    }

    /** Vuelve a valer, y dice de dónde vino esta vez. */
    VerificationStandard reactivated(boolean porLaOrden) {
        return new VerificationStandard(id, idVerificacion, idEquipoPatron, porLaOrden, true);
    }

    VerificationStandard deactivated() {
        return new VerificationStandard(id, idVerificacion, idEquipoPatron, desdeOrden, false);
    }
}
