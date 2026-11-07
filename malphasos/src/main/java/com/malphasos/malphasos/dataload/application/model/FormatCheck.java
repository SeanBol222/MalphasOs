package com.malphasos.malphasos.dataload.application.model;

import java.util.List;

/**
 * Lo que dice el esquema de un archivo: qué contiene —{@code catalogo}, {@code cliente} o
 * {@code historial}, o nulo si no se pudo saber—, lo que incumple, y las {@code ref} que declara y las
 * que usa, para comprobarlas mirando la carga entera.
 */
public record FormatCheck(String contenido, List<DataLoadProblem> errores, List<RefMention> declaradas,
        List<RefMention> usadas) {

    public FormatCheck {
        errores = List.copyOf(errores);
        declaradas = List.copyOf(declaradas);
        usadas = List.copyOf(usadas);
    }

    /** Un archivo del que no se pudo leer nada: solo sus errores. */
    public static FormatCheck unreadable(List<DataLoadProblem> errores) {
        return new FormatCheck(null, errores, List.of(), List.of());
    }
}
