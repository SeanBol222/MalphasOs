package com.malphasos.malphasos.dataload.application.model;

import java.util.List;

/**
 * Lo que dice la validación de una carga. Los errores impiden aplicarla; los avisos se leen y no
 * detienen nada (decidido por el usuario el 2026-11-07). El resumen dice, por sección, cuántos
 * registros se crearían y cuántos se ignorarían porque ya existen.
 */
public record DataLoadReport(List<DataLoadProblem> errores, List<DataLoadProblem> avisos, List<SectionCount> resumen) {

    public DataLoadReport {
        errores = List.copyOf(errores);
        avisos = List.copyOf(avisos);
        resumen = List.copyOf(resumen);
    }

    /** Un informe sin resumen: el de una carga que no se pudo leer. */
    public DataLoadReport(List<DataLoadProblem> errores, List<DataLoadProblem> avisos) {
        this(errores, avisos, List.of());
    }

    /** Se puede aplicar: no tiene ningún error. */
    public boolean valida() {
        return errores.isEmpty();
    }
}
