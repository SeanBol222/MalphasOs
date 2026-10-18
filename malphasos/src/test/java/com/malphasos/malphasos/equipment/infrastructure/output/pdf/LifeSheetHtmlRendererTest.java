package com.malphasos.malphasos.equipment.infrastructure.output.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.equipment.application.services.lifeSheet.UnaHojaDeVida;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDate;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Lo que imprime la hoja de vida en PDF (2026-10-18), leído en el HTML que Gotenberg convierte. Recoge los
 * casos de {@code hoja-de-vida.spec.ts}, que la fijaban cuando la pintaba Angular.
 */
class LifeSheetHtmlRendererTest {

    private static final LifeSheetHtmlRenderer RENDERER = new LifeSheetHtmlRenderer(motor(), "https://malphasos.example/");

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

    private static Document pintar(UnaHojaDeVida hoja) {
        return Jsoup.parse(RENDERER.render(hoja.impresa()));
    }

    private static List<Element> hojas(Document documento) {
        return documento.select(".hv-hoja");
    }

    @Test
    @DisplayName("dos hojas: la banda con el equipo y el QR que lleva a esta hoja de vida en la aplicación")
    void laBanda() {
        Document documento = pintar(new UnaHojaDeVida());

        assertThat(hojas(documento)).hasSize(2);
        assertThat(documento.selectFirst(".hv-banda__titulo").text()).isEqualTo("Báscula Kern MS-200");
        assertThat(documento.selectFirst(".hv-banda__rotulo").text()).isEqualTo("Hoja de vida · HV-CDN-0001");
        assertThat(documento.selectFirst(".hv-qr svg").attr("aria-label"))
                .endsWith("https://malphasos.example/equipos/" + UnaHojaDeVida.ID + "/hoja-de-vida");
        assertThat(documento.selectFirst(".hv-qr path").attr("d")).isNotBlank();
    }

    @Test
    @DisplayName("la hoja 1 solo lleva lo de tamaño fijo; el protocolo, el cuidado y el historial van en la 2")
    void queVaEnCadaHoja() {
        // Decidido por el usuario el 2026-10-17: el protocolo crece con cada paso y desbordaba la hoja 1.
        Document documento = pintar(new UnaHojaDeVida().conPasos(3));
        Element primera = hojas(documento).get(0);
        Element segunda = hojas(documento).get(1);

        assertThat(primera.text()).contains("Identificación", "Cliente", "Qué es", "Características técnicas")
                .doesNotContain("Protocolo preventivo", "Historial de servicio técnico");
        assertThat(segunda.text()).contains("Protocolo preventivo", "Cuidado y limpieza", "Historial de servicio técnico");
        assertThat(segunda.select("ol.hv-lista > li")).hasSize(3);
        assertThat(segunda.selectFirst("ol.hv-lista > li").text())
                .isEqualTo("Paso 1. Qué se hace en el paso 1 Para este equipo: Alcohol isopropílico");
    }

    @Test
    @DisplayName("el número de página no lo escribe la hoja: lo pone el pie de cada página")
    void sinPaginaEnLaHoja() {
        // Decía «Página 2» aunque el historial ocupara tres páginas.
        assertThat(pintar(new UnaHojaDeVida()).text()).doesNotContain("Página");
    }

    @Test
    @DisplayName("el pie dice el código del formato, la fecha y qué página es de cuántas")
    void elPie() {
        Document pie = Jsoup.parse(RENDERER.renderPie(new UnaHojaDeVida().impresa()));

        assertThat(pie.text()).contains("FT-HV-01 · Actualizada el 18 oct 2026");
        assertThat(pie.select(".pageNumber")).hasSize(1);
        assertThat(pie.select(".totalPages")).hasSize(1);
    }

    @Test
    @DisplayName("cada hoja mide la carta menos el margen del pie, que es donde Gotenberg lo pone")
    void altoDeHoja() {
        assertThat(LifeSheetHtmlRenderer.ALTO_DE_HOJA).isEqualTo("10.50in");
        assertThat(pintar(new UnaHojaDeVida()).selectFirst("style").data()).contains("min-height: 10.50in");
    }

    @Test
    @DisplayName("el historial marca el resultado por la forma y deja seis renglones para anotar a mano")
    void elHistorial() {
        Document documento = pintar(new UnaHojaDeVida()
                .con(InterventionResult.OPERATIVO_CON_RESTRICCIONES, LocalDate.of(2026, 10, 4)));

        Element fila = documento.selectFirst("tr.hv-fila");
        assertThat(fila.select("td").get(0).text()).isEqualTo("04 oct 2026");
        assertThat(fila.selectFirst(".hv-resultado").hasClass("hv-rojo")).isTrue();
        assertThat(fila.selectFirst(".hv-marca").hasClass("hv-marca--OPERATIVO_CON_RESTRICCIONES")).isTrue();
        assertThat(documento.select("tr.hv-en-blanco")).hasSize(6);
    }

    @Test
    @DisplayName("un historial vacío lo dice: no se esconde")
    void historialVacio() {
        assertThat(pintar(new UnaHojaDeVida()).text()).contains("Sin intervenciones todavía.");
    }

    @Test
    @DisplayName("sin logo no hay escudo ni imagen en la banda, y sin empresa el documento sale igual")
    void sinLogoNiEmpresa() {
        assertThat(pintar(new UnaHojaDeVida()).select("img")).isEmpty();
        assertThat(hojas(pintar(new UnaHojaDeVida().sinEmpresa()))).hasSize(2);
    }

    @Test
    @DisplayName("un equipo dado de baja lo dice en la banda")
    void deBaja() {
        assertThat(pintar(new UnaHojaDeVida().deBaja()).selectFirst(".hv-banda__resumen").text())
                .endsWith("· De baja");
        assertThat(pintar(new UnaHojaDeVida()).selectFirst(".hv-banda__resumen").text()).doesNotContain("De baja");
    }
}
