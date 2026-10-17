package com.malphasos.malphasos.report.domain.serviceReport;

/** Cómo estaba una parte del equipo al recibirlo para el servicio (V31). */
public enum IntakeState {

    CONFORME,

    /** Exige observación: sin ella el reporte dice que algo estaba mal y no qué. */
    NO_CONFORME,

    /** Esa verificación no aplica a este equipo. */
    NO_APLICA
}
