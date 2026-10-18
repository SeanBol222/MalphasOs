package com.malphasos.malphasos.equipment.application.model.lifeSheet;

/** La hoja de vida en PDF, con el nombre con que se descarga. */
public record LifeSheetPdf(String nombre, byte[] contenido) {
}
