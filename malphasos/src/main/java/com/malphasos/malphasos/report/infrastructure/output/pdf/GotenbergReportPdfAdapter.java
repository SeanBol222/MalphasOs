package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.ReportSheet;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfPort;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfUnavailableException;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.GotenbergPdfConverter;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfConverterUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * El informe en PDF con Gotenberg (2026-10-17, RF-17): el HTML de {@link ReportHtmlRenderer}, convertido.
 * El cliente de Gotenberg es compartido desde el 2026-10-18, cuando la hoja de vida empezó a usarlo.
 */
@Component
@RequiredArgsConstructor
public class GotenbergReportPdfAdapter implements ReportPdfPort {

    private final ReportHtmlRenderer renderer;
    private final GotenbergPdfConverter conversor;

    @Override
    public byte[] render(ReportSheet hoja) {
        try {
            return conversor.convert(renderer.render(hoja));
        } catch (PdfConverterUnavailableException caido) {
            throw new ReportPdfUnavailableException(caido);
        }
    }
}
