package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfUnavailableException;
import com.malphasos.malphasos.report.application.ports.output.WorkOrderSchedulePdfPort;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.GotenbergPdfConverter;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfConverterUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** El cronograma de una orden en PDF con Gotenberg (2026-10-31), en hoja carta horizontal. */
@Component
@RequiredArgsConstructor
public class GotenbergWorkOrderSchedulePdfAdapter implements WorkOrderSchedulePdfPort {

    private final WorkOrderScheduleHtmlRenderer renderer;
    private final GotenbergPdfConverter conversor;

    @Override
    public byte[] render(WorkOrderScheduleSheet hoja) {
        try {
            return conversor.convert(renderer.render(hoja));
        } catch (PdfConverterUnavailableException caido) {
            throw new ReportPdfUnavailableException(caido);
        }
    }
}
