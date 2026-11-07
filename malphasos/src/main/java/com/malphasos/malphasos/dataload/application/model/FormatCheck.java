package com.malphasos.malphasos.dataload.application.model;

import java.util.List;

/**
 * Lo que dice el esquema de un archivo: qué contiene —{@code catalogo}, {@code cliente} o
 * {@code historial}, o nulo si no se pudo saber— y lo que incumple.
 */
public record FormatCheck(String contenido, List<DataLoadProblem> errores) {

    public FormatCheck {
        errores = List.copyOf(errores);
    }
}
