package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.application.model.ReportSheet;

/**
 * Convierte el informe de un reporte en PDF (2026-10-17, RF-17). Lo implementa la plantilla HTML del
 * servidor y Gotenberg, que es Chromium sin pantalla en un contenedor interno.
 */
public interface ReportPdfPort {

    /** @throws ReportPdfUnavailableException si el conversor no responde o falla */
    byte[] render(ReportSheet hoja);
}
