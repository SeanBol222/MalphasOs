package com.malphasos.malphasos.equipment.infrastructure.output.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfUnavailableException;
import com.malphasos.malphasos.equipment.application.services.lifeSheet.UnaHojaDeVida;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.ClosingAtBottom;
import com.malphasos.malphasos.shared.infrastructure.output.pdf.GotenbergPdfConverter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * La hoja de vida en PDF contra un Gotenberg de verdad (2026-10-18): la misma imagen que corre en el
 * compose. Lo que se imprime lo fija {@link LifeSheetHtmlRendererTest}; aquí, que una hoja sea una página.
 * Es lo que falló al imprimir desde el navegador: la hoja 1 no cabía en una carta.
 */
@Testcontainers
class GotenbergLifeSheetPdfAdapterTest {

    @Container
    private static final GenericContainer<?> GOTENBERG = new GenericContainer<>("gotenberg/gotenberg:8.38.1")
            .withExposedPorts(3000)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    private static GotenbergLifeSheetPdfAdapter adaptador;

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
        adaptador = new GotenbergLifeSheetPdfAdapter(new LifeSheetHtmlRenderer(motor(), "http://localhost:5173"),
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
    @DisplayName("una hoja de vida con unos pocos servicios son dos páginas: la hoja 1 cabe en una carta")
    void dosPaginas() {
        byte[] pdf = adaptador.render(new UnaHojaDeVida().conPasos(3).conServicios(2).impresa());

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(paginas(pdf)).isEqualTo(2);
    }

    @Test
    @DisplayName("con un protocolo largo y cuarenta servicios la hoja 2 sigue en más páginas, sin recortar nada")
    void historialLargo() {
        byte[] pdf = adaptador.render(new UnaHojaDeVida().conPasos(9).conServicios(40).impresa());

        assertThat(paginas(pdf)).isGreaterThan(3);
    }

    @ParameterizedTest(name = "con {0} servicios")
    @ValueSource(ints = {0, 2, 8, 12, 20, 25, 30, 35, 40})
    @DisplayName("la empresa y las firmas van al pie de la última página, quepan con el historial o vayan solas (2026-11-21)")
    void cierreAlPie(int servicios) {
        // Con 40 el cierre no cabe tras el historial y va solo a la quinta: antes quedaba arriba de ella.
        // La línea objetivo es la del pie de una hoja que cabe en una página. Con varias puede quedar algo
        // más abajo sin que nadie lo baje —es donde corta Chromium—, pero nunca sobre el pie numerado.
        byte[] pdf = adaptador.render(new UnaHojaDeVida().conPasos(9).conServicios(servicios).impresa());

        ClosingAtBottom.Posicion cierre = ClosingAtBottom.posicion(pdf, LifeSheetHtmlRenderer.CIERRE).orElseThrow();
        assertThat(cierre.pagina()).isEqualTo(paginas(pdf));
        assertThat(cierre.base()).isBetween(
                LifeSheetHtmlRenderer.CIERRE.baseObjetivo() - 2, LifeSheetHtmlRenderer.CIERRE.limite());
    }

    @Test
    @DisplayName("si el conversor no responde, lo dice con la excepción de la hoja de vida")
    void sinConversor() {
        GotenbergLifeSheetPdfAdapter caido = new GotenbergLifeSheetPdfAdapter(
                new LifeSheetHtmlRenderer(motor(), "http://localhost:5173"), new GotenbergPdfConverter("http://localhost:1"));

        assertThatThrownBy(() -> caido.render(new UnaHojaDeVida().impresa()))
                .isInstanceOf(LifeSheetPdfUnavailableException.class);
    }

    @Test
    @DisplayName("manda el pie aparte, con su margen: es lo que numera las páginas")
    void mandaElPie() {
        // Sin el pie, el PDF saldría igual de bien y sin número de página: el conteo de páginas no lo ve.
        GotenbergPdfConverter conversor = Mockito.mock(GotenbergPdfConverter.class);
        LifeSheetHtmlRenderer renderer = new LifeSheetHtmlRenderer(motor(), "http://localhost:5173");
        new GotenbergLifeSheetPdfAdapter(renderer, conversor).render(new UnaHojaDeVida().impresa());

        ArgumentCaptor<String> pie = ArgumentCaptor.forClass(String.class);
        Mockito.verify(conversor).convertWithClosingAtBottom(
                Mockito.any(),
                pie.capture(),
                Mockito.eq(LifeSheetHtmlRenderer.MARGEN_DEL_PIE),
                Mockito.eq(LifeSheetHtmlRenderer.CIERRE));
        assertThat(pie.getValue()).contains("pageNumber", "totalPages", "FT-HV-01");
    }
}
