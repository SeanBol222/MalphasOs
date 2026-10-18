package com.malphasos.malphasos.equipment.application.ports.output;

/**
 * La hoja de vida no se pudo convertir en PDF porque el conversor no respondió o falló (2026-10-18). No es
 * culpa de lo pedido y se puede volver a intentar: por eso tiene su código y no sale como un 500.
 */
public class LifeSheetPdfUnavailableException extends RuntimeException {

    public LifeSheetPdfUnavailableException(Throwable causa) {
        super("No se pudo generar el PDF de la hoja de vida: el conversor no respondió", causa);
    }
}
