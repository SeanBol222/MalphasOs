package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;

/**
 * Convierte la hoja de vida en PDF (2026-10-18). Lo implementa la plantilla HTML del servidor y Gotenberg,
 * como el informe de un reporte.
 */
public interface LifeSheetPdfPort {

    /** @throws LifeSheetPdfUnavailableException si el conversor no responde o falla */
    byte[] render(LifeSheetPrint hoja);
}
