package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.ReportSheet;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfFonts;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.QrSvg;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Pinta el informe de un reporte en HTML con la plantilla {@code templates/informe.html} (2026-10-17). Va
 * aparte de la conversión a PDF para poder probar qué se imprime sin levantar Gotenberg.
 *
 * <p>Lo único que añade a la hoja es lo que depende de dónde corre la aplicación: el QR lleva a este
 * informe en el frontend, en la dirección pública que diga {@code app.frontend-url}.
 */
@Component
public class ReportHtmlRenderer {

    private static final Map<String, Integer> LIENZO = Map.of(
            "ANCHO", ReportSheet.Grafica.ANCHO,
            "ALTO", ReportSheet.Grafica.ALTO,
            "IZQUIERDA", ReportSheet.Grafica.IZQUIERDA,
            "DERECHA", ReportSheet.Grafica.DERECHA,
            "ARRIBA", ReportSheet.Grafica.ARRIBA,
            "ABAJO", ReportSheet.Grafica.ABAJO);

    private final ITemplateEngine plantillas;
    private final String frontend;

    public ReportHtmlRenderer(ITemplateEngine plantillas, @Value("${app.frontend-url}") String frontend) {
        this.plantillas = plantillas;
        this.frontend = frontend.endsWith("/") ? frontend.substring(0, frontend.length() - 1) : frontend;
    }

    public String render(ReportSheet hoja) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariable("hoja", hoja);
        contexto.setVariable("qr", QrSvg.of(frontend + "/reportes/" + hoja.idReporte() + "/documento"));
        contexto.setVariable("lienzo", LIENZO);
        contexto.setVariable("svg", new Svg());
        contexto.setVariable("fuenteLatina", PdfFonts.LATINA);
        contexto.setVariable("fuenteLatinaExtendida", PdfFonts.LATINA_EXTENDIDA);

        return plantillas.process("informe", contexto);
    }

    /** Las coordenadas del SVG, con punto decimal sea cual sea el idioma: con coma, el SVG no se dibuja. */
    public static final class Svg {

        public String n(double valor) {
            return String.format(Locale.ROOT, "%.2f", valor);
        }
    }
}
