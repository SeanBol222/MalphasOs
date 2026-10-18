package com.malphasos.malphasos.report.infrastructure.output.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.report.application.ports.output.ReportPdfUnavailableException;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.GotenbergPdfConverter;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.ReportSheetComposer;
import com.malphasos.malphasos.report.application.sheet.UnInforme;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * El informe en PDF contra un Gotenberg de verdad (2026-10-17, RF-17): la misma imagen que corre en el
 * compose. Lo que se imprime lo fija {@link ReportHtmlRendererTest}; aquí, que la conversión funcione y
 * que una hoja carta sea una hoja: una página sin lecturas y dos con ellas.
 */
@Testcontainers
class GotenbergReportPdfAdapterTest {

    @Container
    private static final GenericContainer<?> GOTENBERG = new GenericContainer<>("gotenberg/gotenberg:8.38.1")
            .withExposedPorts(3000)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    private static GotenbergReportPdfAdapter adaptador;

    private static SpringTemplateEngine motor() {
        ClassLoaderTemplateResolver plantillas = new ClassLoaderTemplateResolver();
        plantillas.setPrefix("templates/");
        plantillas.setSuffix(".html");
        plantillas.setTemplateMode(TemplateMode.HTML);
        plantillas.setCharacterEncoding("UTF-8");
        SpringTemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(plantillas);

        return motor;
    }

    @BeforeAll
    static void conectar() {
        adaptador = new GotenbergReportPdfAdapter(new ReportHtmlRenderer(motor(), "http://localhost:5173"),
                new GotenbergPdfConverter("http://" + GOTENBERG.getHost() + ":" + GOTENBERG.getMappedPort(3000)));
    }

    /** Las páginas del PDF: cada una es un objeto {@code /Type /Page}, que no es {@code /Pages}. */
    private static int paginas(byte[] pdf) {
        Matcher pagina = Pattern.compile("/Type\\s*/Page(?!s)").matcher(new String(pdf, StandardCharsets.ISO_8859_1));
        int cuantas = 0;
        while (pagina.find()) {
            cuantas++;
        }

        return cuantas;
    }

    @Test
    @DisplayName("sin lecturas, un PDF de una hoja")
    void unaHoja() {
        byte[] pdf = adaptador.render(ReportSheetComposer.compose(new UnInforme().vista()));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(paginas(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("con lecturas, dos: la segunda es la verificación metrológica")
    void dosHojas() {
        byte[] pdf = adaptador.render(ReportSheetComposer.compose(new UnInforme().conLecturas().vista()));

        assertThat(paginas(pdf)).isEqualTo(2);
    }

    @Test
    @DisplayName("si el conversor no responde, lo dice con su excepción y no con la del cliente HTTP")
    void sinConversor() {
        GotenbergReportPdfAdapter caido = new GotenbergReportPdfAdapter(
                new ReportHtmlRenderer(motor(), "http://localhost:5173"), new GotenbergPdfConverter("http://localhost:1"));

        assertThatThrownBy(() -> caido.render(ReportSheetComposer.compose(new UnInforme().vista())))
                .isInstanceOf(ReportPdfUnavailableException.class);
    }

    @Test
    @DisplayName("con muchas lecturas la verificación sigue en otra hoja: no se recorta ninguna")
    void muchasLecturas() {
        // La hoja tenía altura fija con overflow oculto: lo que no cabía desaparecía del PDF sin avisar.
        UnInforme informe = new UnInforme().conLecturas();
        for (int i = 3; i <= 60; i++) {
            informe.lecturas.add(com.malphasos.malphasos.report.domain.serviceReport.VerificationReading.rehydrate(
                    java.util.UUID.randomUUID(), UnInforme.VERIFICACION, UnInforme.PUNTO, i,
                    new java.math.BigDecimal("100"), new java.math.BigDecimal("100.1"), "mmHg", true,
                    new java.math.BigDecimal("0.5")));
        }

        byte[] pdf = adaptador.render(ReportSheetComposer.compose(informe.vista()));

        assertThat(paginas(pdf)).isGreaterThan(2);
    }
}
