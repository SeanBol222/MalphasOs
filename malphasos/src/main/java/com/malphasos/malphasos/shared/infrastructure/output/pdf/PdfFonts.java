package com.malphasos.malphasos.shared.infrastructure.output.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;

/**
 * Archivo, la fuente de la marca, para los documentos en PDF: va dentro del HTML como {@code data:},
 * porque Gotenberg no sale a internet a buscarla. Dos cortes, latino y latino extendido, como los sirve
 * Google Fonts; la licencia está junto a ellos, en {@code pdf/ARCHIVO-OFL.txt}.
 */
public final class PdfFonts {

    public static final String LATINA = dataUri("pdf/archivo-latin-wght-normal.woff2");
    public static final String LATINA_EXTENDIDA = dataUri("pdf/archivo-latin-ext-wght-normal.woff2");

    private PdfFonts() {
    }

    private static String dataUri(String ruta) {
        try (InputStream bytes = new ClassPathResource(ruta).getInputStream()) {
            return "data:font/woff2;base64," + Base64.getEncoder().encodeToString(bytes.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Falta la fuente de los documentos en PDF: " + ruta, e);
        }
    }
}
