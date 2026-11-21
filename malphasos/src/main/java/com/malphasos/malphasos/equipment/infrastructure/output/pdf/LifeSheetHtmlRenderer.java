package com.malphasos.malphasos.equipment.infrastructure.output.pdf;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.ClosingAtBottom;
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

    /**
     * El cierre de la hoja 2 —la empresa y las dos líneas de firma— al pie de su última página
     * (2026-11-21). La marca es la última firma; por debajo del alto de hoja solo está el pie numerado.
     * La línea de base objetivo se midió en el PDF de una hoja que cabe, donde la deja el CSS.
     */
    public static final ClosingAtBottom.Spec CIERRE =
            new ClosingAtBottom.Spec("Recibió · Profesional responsable", 732.75, (11 - MARGEN_DEL_PIE) * 72);

    /** Las líneas en blanco del historial cuando no hay que bajar nada: para anotar a mano. */
    static final int FILAS_EN_BLANCO = 6;

    /** Lo que ocupa una línea en blanco, en píxeles CSS: medido en el PDF, con su borde. */
    static final double ALTO_DE_FILA = 44;

    /** El margen del cierre sobre el historial, el de la plantilla. */
    private static final double MARGEN_DEL_CIERRE = 8;

    private final ITemplateEngine plantillas;
    private final String frontend;

    public LifeSheetHtmlRenderer(ITemplateEngine plantillas, @Value("${app.frontend-url}") String frontend) {
        this.plantillas = plantillas;
        this.frontend = frontend.endsWith("/") ? frontend.substring(0, frontend.length() - 1) : frontend;
    }

    public String render(LifeSheetPrint hoja) {
        return render(hoja, 0);
    }

    /**
     * Con el cierre bajado tantos píxeles: ver {@link ClosingAtBottom}. Lo que baja lo llenan líneas en
     * blanco del historial, hasta las firmas (2026-11-21, pedido por el usuario); lo que no llega a una
     * línea entera queda de margen.
     */
    public String render(LifeSheetPrint hoja, double bajadaDelCierre) {
        int filas = (int) Math.floor(Math.max(bajadaDelCierre, 0) / ALTO_DE_FILA);
        double resto = bajadaDelCierre - filas * ALTO_DE_FILA;

        Context contexto = contexto(hoja);
        contexto.setVariable("filasEnBlanco", FILAS_EN_BLANCO + filas);
        contexto.setVariable("estiloDelCierre", resto > 0 ? ClosingAtBottom.estilo(MARGEN_DEL_CIERRE + resto) : null);
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
