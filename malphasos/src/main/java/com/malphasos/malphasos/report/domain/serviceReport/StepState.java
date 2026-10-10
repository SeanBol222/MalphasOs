package com.malphasos.malphasos.report.domain.serviceReport;

/** En qué quedó un paso del protocolo dentro de un reporte. */
public enum StepState {
    /** Todavía no se ha marcado. Un reporte no se cierra con pasos así. */
    PENDIENTE,
    HECHO,
    /** No se hizo porque no aplica a este equipo. Exige una nota que diga por qué. */
    NO_APLICA
}
