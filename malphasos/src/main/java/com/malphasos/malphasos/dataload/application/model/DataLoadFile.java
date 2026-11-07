package com.malphasos.malphasos.dataload.application.model;

/**
 * Un archivo de una carga, tal como se subió: su nombre, para decir en cuál está cada problema, y sus
 * bytes, sin leer todavía.
 */
public record DataLoadFile(String nombre, byte[] contenido) {
}
