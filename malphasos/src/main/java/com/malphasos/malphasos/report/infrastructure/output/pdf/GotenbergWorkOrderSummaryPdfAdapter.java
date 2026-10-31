package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfUnavailableException;
import com.malphasos.malphasos.report.application.ports.output.WorkOrderSummaryPdfPort;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.GotenbergPdfConverter;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfConverterUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** El resumen de una orden en PDF con Gotenberg (2026-10-31), en hoja carta horizontal. */
@Component
@RequiredArgsConstructor
public class GotenbergWorkOrderSummaryPdfAdapter implements WorkOrderSummaryPdfPort {

    private final WorkOrderSummaryHtmlRenderer renderer;
    private final GotenbergPdfConverter conversor;

    @Override
    public byte[] render(WorkOrderSummarySheet hoja) {
        try {
            return conversor.convert(renderer.render(hoja));
        } catch (PdfConverterUnavailableException caido) {
            throw new ReportPdfUnavailableException(caido);
        }
    }
}
