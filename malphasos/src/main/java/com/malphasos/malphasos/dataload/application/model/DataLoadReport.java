package com.malphasos.malphasos.dataload.application.model;

import java.util.List;

/**
 * Lo que dice la validación de una carga. Los errores impiden aplicarla; los avisos se leen y no
 * detienen nada (decidido por el usuario el 2026-11-07).
 */
public record DataLoadReport(List<DataLoadProblem> errores, List<DataLoadProblem> avisos) {

    public DataLoadReport {
        errores = List.copyOf(errores);
        avisos = List.copyOf(avisos);
    }

    /** Se puede aplicar: no tiene ningún error. */
    public boolean valida() {
        return errores.isEmpty();
    }
}
