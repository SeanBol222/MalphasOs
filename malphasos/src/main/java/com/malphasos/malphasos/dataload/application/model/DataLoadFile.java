package com.malphasos.malphasos.dataload.application.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Un archivo de una carga, tal como se subió: su nombre, para decir en cuál está cada problema, y sus
 * bytes, sin leer todavía.
 *
 * <p>Un record compara un arreglo por referencia: {@code equals} y {@code hashCode} comparan los bytes, y
 * {@code toString} dice cuántos son en vez de volcarlos.
 */
public record DataLoadFile(String nombre, byte[] contenido) {

    @Override
    public boolean equals(Object otro) {
        return otro instanceof DataLoadFile o && Objects.equals(nombre, o.nombre)
                && Arrays.equals(contenido, o.contenido);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(nombre) + Arrays.hashCode(contenido);
    }

    @Override
    public String toString() {
        return "DataLoadFile[nombre=" + nombre + ", contenido="
                + (contenido == null ? null : contenido.length + " bytes") + "]";
    }
}
