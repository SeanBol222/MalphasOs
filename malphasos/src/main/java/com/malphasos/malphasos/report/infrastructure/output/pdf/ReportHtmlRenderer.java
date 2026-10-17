package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.ReportSheet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
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
    private final String fuenteLatina;
    private final String fuenteLatinaExtendida;

    public ReportHtmlRenderer(ITemplateEngine plantillas, @Value("${app.frontend-url}") String frontend) {
        this.plantillas = plantillas;
        this.frontend = frontend.endsWith("/") ? frontend.substring(0, frontend.length() - 1) : frontend;
        this.fuenteLatina = fuente("pdf/archivo-latin-wght-normal.woff2");
        this.fuenteLatinaExtendida = fuente("pdf/archivo-latin-ext-wght-normal.woff2");
    }

    public String render(ReportSheet hoja) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariable("hoja", hoja);
        contexto.setVariable("qr", ReportQr.of(frontend + "/reportes/" + hoja.idReporte() + "/documento"));
        contexto.setVariable("lienzo", LIENZO);
        contexto.setVariable("svg", new Svg());
        contexto.setVariable("fuenteLatina", fuenteLatina);
        contexto.setVariable("fuenteLatinaExtendida", fuenteLatinaExtendida);

        return plantillas.process("informe", contexto);
    }

    /** Las coordenadas del SVG, con punto decimal sea cual sea el idioma: con coma, el SVG no se dibuja. */
    public static final class Svg {

        public String n(double valor) {
            return String.format(Locale.ROOT, "%.2f", valor);
        }
    }

    private static String fuente(String ruta) {
        try (InputStream bytes = new ClassPathResource(ruta).getInputStream()) {
            return "data:font/woff2;base64," + Base64.getEncoder().encodeToString(bytes.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Falta la fuente del informe: " + ruta, e);
        }
    }
}
