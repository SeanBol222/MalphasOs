package com.malphasos.malphasos.company.infrastructure.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lee el logo del archivo de arranque, para sembrarlo con la empresa.
 *
 * <p>Solo lee: si es una imagen admitida y si cabe lo decide {@code CompanyLogo}, la misma regla que
 * para un logo subido desde la pantalla. Un logo configurado que no existe tumba el arranque —es
 * preferible a sembrar la empresa sin él sin que nadie se entere—, pero solo cuando hay algo que
 * sembrar: con la empresa ya en la base, el archivo no se mira.
 */
final class ServiceCompanyLogo {

    private ServiceCompanyLogo() {
    }

    /** Los bytes del logo, o nulo si el archivo no configura ninguno. */
    static byte[] leer(String directorio, String logo) {
        if (logo == null || logo.isBlank()) {
            return null;
        }

        Path archivo = Path.of(directorio == null ? "." : directorio).resolve(logo);
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No se encuentra el logo de la empresa en " + archivo.toAbsolutePath());
        }

        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo de la empresa en " + archivo, e);
        }
    }
}
