package com.malphasos.malphasos.company.application.services.serviceCompany.commands;

import java.util.Arrays;

/**
 * El logo nuevo, tal como se subió: el tipo lo deducen sus bytes, no quien lo sube.
 *
 * <p>Un record compara un arreglo por referencia: {@code equals} y {@code hashCode} comparan los bytes, y
 * {@code toString} dice cuántos son en vez de volcarlos.
 */
public record ChangeCompanyLogoCommand(byte[] contenido) {

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ChangeCompanyLogoCommand o && Arrays.equals(contenido, o.contenido);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(contenido);
    }

    @Override
    public String toString() {
        return "ChangeCompanyLogoCommand[contenido=" + (contenido == null ? null : contenido.length + " bytes") + "]";
    }
}
