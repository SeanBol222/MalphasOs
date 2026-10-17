package com.malphasos.malphasos.report.domain.serviceReport;

/**
 * Una verificación al ingreso: cómo estaba una parte del equipo al recibirlo, y por qué (V31,
 * 2026-10-16). Son dos en todo reporte —estructura física y estado operativo—, decidido por el usuario
 * a partir de los informes que la empresa entrega hoy.
 *
 * @param observacion opcional, salvo si no es conforme. Recortada; en blanco es que no hay
 */
public record IntakeCheck(IntakeState estado, String observacion) {

    static final int LARGO_OBSERVACION = 500;

    public IntakeCheck {
        if (estado == null) {
            throw new IllegalArgumentException("Una verificacion al ingreso necesita su estado");
        }

        observacion = observacion == null || observacion.isBlank() ? null : observacion.trim();

        if (estado == IntakeState.NO_CONFORME && observacion == null) {
            throw new IllegalArgumentException(
                    "Una verificacion al ingreso no conforme necesita una observacion que diga que estaba mal");
        }
        if (observacion != null && observacion.length() > LARGO_OBSERVACION) {
            throw new IllegalArgumentException(
                    "La observacion de una verificacion al ingreso tiene como mucho " + LARGO_OBSERVACION
                            + " caracteres");
        }
    }
}
