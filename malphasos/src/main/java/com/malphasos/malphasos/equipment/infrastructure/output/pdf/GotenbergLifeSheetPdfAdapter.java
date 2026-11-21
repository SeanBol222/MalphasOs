package com.malphasos.malphasos.equipment.infrastructure.output.pdf;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfPort;
import com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfUnavailableException;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.GotenbergPdfConverter;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfConverterUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * La hoja de vida en PDF con Gotenberg (2026-10-18), con el pie numerado en cada página y, desde el
 * 2026-11-21, las firmas al pie de la última.
 */
@Component
@RequiredArgsConstructor
public class GotenbergLifeSheetPdfAdapter implements LifeSheetPdfPort {

    private final LifeSheetHtmlRenderer renderer;
    private final GotenbergPdfConverter conversor;

    @Override
    public byte[] render(LifeSheetPrint hoja) {
        try {
            return conversor.convertWithClosingAtBottom(
                    bajada -> renderer.render(hoja, bajada),
                    renderer.renderPie(hoja),
                    LifeSheetHtmlRenderer.MARGEN_DEL_PIE,
                    LifeSheetHtmlRenderer.CIERRE);
        } catch (PdfConverterUnavailableException caido) {
            throw new LifeSheetPdfUnavailableException(caido);
        }
    }
}
