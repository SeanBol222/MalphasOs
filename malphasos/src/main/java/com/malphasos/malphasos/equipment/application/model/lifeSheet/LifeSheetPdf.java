package com.malphasos.malphasos.equipment.application.model.lifeSheet;

import java.util.Arrays;
import java.util.Objects;

/**
 * La hoja de vida en PDF, con el nombre con que se descarga.
 *
 * <p>Un record compara un arreglo por referencia: {@code equals} y {@code hashCode} comparan los bytes, y
 * {@code toString} dice cuántos son en vez de volcarlos.
 */
public record LifeSheetPdf(String nombre, byte[] contenido) {

    @Override
    public boolean equals(Object otro) {
        return otro instanceof LifeSheetPdf o && Objects.equals(nombre, o.nombre)
                && Arrays.equals(contenido, o.contenido);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(nombre) + Arrays.hashCode(contenido);
    }

    @Override
    public String toString() {
        return "LifeSheetPdf[nombre=" + nombre + ", contenido="
                + (contenido == null ? null : contenido.length + " bytes") + "]";
    }
}
