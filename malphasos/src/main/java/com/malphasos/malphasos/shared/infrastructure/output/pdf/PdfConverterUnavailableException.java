package com.malphasos.malphasos.shared.infrastructure.output.pdf;

/**
 * Gotenberg no respondió o falló. Cada módulo la traduce a la suya, con su código de error: quien pide
 * un informe y quien pide una hoja de vida reciben cada uno el mensaje de su documento.
 */
public class PdfConverterUnavailableException extends RuntimeException {

    public PdfConverterUnavailableException(Throwable causa) {
        super("El conversor de PDF no respondió", causa);
    }
}
