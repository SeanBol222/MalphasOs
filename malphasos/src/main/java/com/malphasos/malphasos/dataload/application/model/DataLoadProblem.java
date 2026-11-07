package com.malphasos.malphasos.dataload.application.model;

/**
 * Un problema de una carga: en qué archivo, en qué lugar de él —un puntero JSON, como
 * {@code /catalogo/protocolos/9/nombre}, o nulo si es del archivo entero— y qué pasa.
 */
public record DataLoadProblem(String archivo, String ruta, String mensaje) {

    /** Un problema del archivo entero, o de la carga si el archivo es nulo. */
    public static DataLoadProblem of(String archivo, String mensaje) {
        return new DataLoadProblem(archivo, null, mensaje);
    }
}
