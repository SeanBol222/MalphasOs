package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfFonts;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.QrSvg;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Pinta el cronograma de una orden con {@code templates/cronograma-orden.html} (2026-10-31), aparte de la
 * conversión como {@link WorkOrderSummaryHtmlRenderer}. El QR lleva a la ficha de la orden.
 */
@Component
public class WorkOrderScheduleHtmlRenderer {

    private final ITemplateEngine plantillas;
    private final String frontend;

    public WorkOrderScheduleHtmlRenderer(ITemplateEngine plantillas, @Value("${app.frontend-url}") String frontend) {
        this.plantillas = plantillas;
        this.frontend = frontend.endsWith("/") ? frontend.substring(0, frontend.length() - 1) : frontend;
    }

    public String render(WorkOrderScheduleSheet hoja) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariable("hoja", hoja);
        contexto.setVariable("qr", QrSvg.of(frontend + "/ordenes/" + hoja.idOrden()));
        contexto.setVariable("fuenteLatina", PdfFonts.LATINA);
        contexto.setVariable("fuenteLatinaExtendida", PdfFonts.LATINA_EXTENDIDA);

        return plantillas.process("cronograma-orden", contexto);
    }
}
