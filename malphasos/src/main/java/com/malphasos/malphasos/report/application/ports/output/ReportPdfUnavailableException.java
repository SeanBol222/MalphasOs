package com.malphasos.malphasos.report.application.ports.output;

/**
 * El PDF no se pudo generar porque el conversor no respondió o falló (2026-10-17). No es culpa de lo
 * pedido, y se puede volver a intentar: por eso tiene su código y no sale como un 500 sin explicación.
 */
public class ReportPdfUnavailableException extends RuntimeException {

    public ReportPdfUnavailableException(Throwable causa) {
        super("No se pudo generar el PDF del informe: el conversor no respondió", causa);
    }
}
