package com.malphasos.malphasos.company.application.services.serviceCompany.commands;

import java.util.Arrays;
import java.util.Objects;

/**
 * La empresa del archivo de arranque, para una base que todavía no tiene ninguna.
 *
 * <p>Un record compara un arreglo por referencia: {@code equals} y {@code hashCode} comparan los bytes, y
 * {@code toString} dice cuántos son en vez de volcarlos.
 *
 * @param logo los bytes del logo del archivo; nulo si no configura ninguno
 */
public record SeedServiceCompanyCommand(SaveServiceCompanyCommand datos, byte[] logo) {

    @Override
    public boolean equals(Object otro) {
        return otro instanceof SeedServiceCompanyCommand o
                && Objects.equals(datos, o.datos) && Arrays.equals(logo, o.logo);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(datos) + Arrays.hashCode(logo);
    }

    @Override
    public String toString() {
        return "SeedServiceCompanyCommand[datos=" + datos + ", logo=" + (logo == null ? null : logo.length + " bytes")
                + "]";
    }
}
