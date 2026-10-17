package com.malphasos.malphasos.report.domain.serviceReport;

import java.util.Objects;
import java.util.UUID;

/**
 * Un accesorio con el que llegó el equipo al servicio, en su orden (V31, 2026-10-16): el cable, la
 * sonda, el brazalete. Uno no conforme dice por qué.
 *
 * <p>No es un agregado: vive y muere con su reporte. Se registran todos a la vez y sustituyen a los
 * anteriores, que se retiran sin borrarse, como las lecturas.
 */
public record ReportAccessory(
        UUID id, int orden, String nombre, boolean conforme, String observacion, boolean estadoActivo) {

    static final int LARGO_NOMBRE = 120;
    static final int LARGO_OBSERVACION = 500;

    public ReportAccessory {
        if (id == null) {
            throw new IllegalArgumentException("Un accesorio necesita su identificador");
        }
        if (orden < 1) {
            throw new IllegalArgumentException("El orden de un accesorio empieza en 1");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un accesorio necesita su nombre");
        }

        nombre = nombre.trim();
        observacion = observacion == null || observacion.isBlank() ? null : observacion.trim();

        if (nombre.length() > LARGO_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre de un accesorio tiene como mucho " + LARGO_NOMBRE + " caracteres");
        }
        if (!conforme && observacion == null) {
            throw new IllegalArgumentException(
                    "El accesorio " + nombre + " no es conforme: hay que decir por que");
        }
        if (observacion != null && observacion.length() > LARGO_OBSERVACION) {
            throw new IllegalArgumentException(
                    "La observacion de un accesorio tiene como mucho " + LARGO_OBSERVACION + " caracteres");
        }
    }

    /** Uno nuevo, activo. */
    public static ReportAccessory of(int orden, String nombre, boolean conforme, String observacion) {
        return new ReportAccessory(UUID.randomUUID(), orden, nombre, conforme, observacion, true);
    }

    /** Si dice lo mismo que otro, sin mirar su identificador ni su estado. */
    boolean diceLoMismoQue(ReportAccessory otro) {
        return orden == otro.orden && nombre.equals(otro.nombre) && conforme == otro.conforme
                && Objects.equals(observacion, otro.observacion);
    }

    ReportAccessory deactivated() {
        return new ReportAccessory(id, orden, nombre, conforme, observacion, false);
    }
}
