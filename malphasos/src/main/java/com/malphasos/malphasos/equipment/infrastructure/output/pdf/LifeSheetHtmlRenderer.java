package com.malphasos.malphasos.equipment.infrastructure.output.pdf;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.PdfFonts;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.QrSvg;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Pinta la hoja de vida en HTML con {@code templates/hoja-de-vida.html}, y su pie con
 * {@code templates/hoja-de-vida-pie.html} (2026-10-18). Va aparte de la conversión para poder probar qué
 * se imprime sin levantar Gotenberg.
 *
 * <p>Lo único que añade a la hoja es lo que depende de dónde corre la aplicación: el QR lleva a esta hoja
 * de vida en el frontend, en la dirección pública que diga {@code app.frontend-url}, y pide iniciar
 * sesión —decidido el 2026-10-05, sin enlace público—.
 */
@Component
public class LifeSheetHtmlRenderer {

    /** El margen de abajo de cada página, donde va el pie, en pulgadas. */
    public static final double MARGEN_DEL_PIE = 0.5;

    /** El alto de cada hoja: la carta menos el margen del pie. */
    static final String ALTO_DE_HOJA = String.format(Locale.ROOT, "%.2fin", 11 - MARGEN_DEL_PIE);

    private final ITemplateEngine plantillas;
    private final String frontend;

    public LifeSheetHtmlRenderer(ITemplateEngine plantillas, @Value("${app.frontend-url}") String frontend) {
        this.plantillas = plantillas;
        this.frontend = frontend.endsWith("/") ? frontend.substring(0, frontend.length() - 1) : frontend;
    }

    public String render(LifeSheetPrint hoja) {
        Context contexto = contexto(hoja);
        contexto.setVariable("qr", QrSvg.of(frontend + "/equipos/" + hoja.idEquipoCliente() + "/hoja-de-vida"));
        contexto.setVariable("altoDeHoja", ALTO_DE_HOJA);
        contexto.setVariable("fuenteLatinaExtendida", PdfFonts.LATINA_EXTENDIDA);

        return plantillas.process("hoja-de-vida", contexto);
    }

    public String renderPie(LifeSheetPrint hoja) {
        return plantillas.process("hoja-de-vida-pie", contexto(hoja));
    }

    private static Context contexto(LifeSheetPrint hoja) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariable("hoja", hoja);
        contexto.setVariable("fuenteLatina", PdfFonts.LATINA);

        return contexto;
    }
}
