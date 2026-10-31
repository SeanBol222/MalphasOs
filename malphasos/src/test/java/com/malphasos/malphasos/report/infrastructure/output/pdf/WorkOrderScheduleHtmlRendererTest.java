package com.malphasos.malphasos.report.infrastructure.output.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderScheduleComposer;
import com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Lo que imprime el cronograma de una orden (2026-10-31), leído en el HTML que Gotenberg convierte. */
class WorkOrderScheduleHtmlRendererTest {

    private static final WorkOrderScheduleHtmlRenderer RENDERER =
            new WorkOrderScheduleHtmlRenderer(motor(), "https://malphasos.example/");
    private static final DocumentImage LOGO =
            new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1}, "image/png");

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

    private static Document pintar(String empresa, DocumentImage logo) {
        WorkOrderDocument base = UnaOrdenAtendida.documento(LocalDate.of(2026, 10, 18));
        WorkOrderDocument semestral = new WorkOrderDocument(base.version(), base.numero(), base.tipoServicio(),
                base.garantia(), "SEMESTRAL", base.fechaMantenimiento(), base.fechaEjecucion(), base.cliente(),
                base.sede(), base.ingeniero(), base.equipos());
        WorkOrderScheduleSheet hoja = WorkOrderScheduleComposer.compose(UnaOrdenAtendida.ORDEN, semestral,
                UnaOrdenAtendida.reportes(), empresa, logo);

        return Jsoup.parse(RENDERER.render(hoja));
    }

    @Test
    @DisplayName("la banda dice la empresa con su logo, la orden, el cliente y el ingeniero")
    void laBanda() {
        Document hoja = pintar("Empresa de Prueba", LOGO);

        assertThat(hoja.selectFirst("[data-empresa]").text()).isEqualTo("Empresa de Prueba");
        assertThat(hoja.selectFirst(".cr-empresa img").attr("src")).isEqualTo(LOGO.dataUri());
        assertThat(hoja.selectFirst("[data-servicio]").text())
                .isEqualTo("Orden N.º 1DC6F5 · Preventivo semestral · Ejecutada el 18 oct 2026");
        assertThat(hoja.selectFirst("[data-cliente]").text())
                .contains("Clínica de Ejemplo S.A.S.").contains("Ingeniero: Ingeniera de Prueba");
        assertThat(hoja.title()).isEqualTo("Cronograma de la orden N.º 1DC6F5");
    }

    @Test
    @DisplayName("sin empresa configurada no imprime la línea de la empresa")
    void sinEmpresa() {
        assertThat(pintar(null, null).selectFirst(".cr-empresa")).isNull();
    }

    @Test
    @DisplayName("el QR lleva a la ficha de la orden en el frontend")
    void elQr() {
        assertThat(pintar("E", null).selectFirst("[data-qr]").attr("aria-label"))
                .isEqualTo("Código QR: https://malphasos.example/ordenes/" + UnaOrdenAtendida.ORDEN);
    }

    @Test
    @DisplayName("trece columnas de meses, con el año en la primera y en enero")
    void losMeses() {
        Document hoja = pintar("E", null);

        assertThat(hoja.select("[data-mes]")).hasSize(13);
        assertThat(hoja.select("[data-mes]").get(0).text()).isEqualTo("oct2026");
        assertThat(hoja.select("[data-mes]").get(1).text()).isEqualTo("nov");
        assertThat(hoja.select("[data-mes]").get(3).text()).isEqualTo("ene2027");
    }

    @Test
    @DisplayName("cada fila marca lo realizado y lo programado, y el fuera de servicio no se programa")
    void lasFilas() {
        Document hoja = pintar("E", null);

        assertThat(hoja.select("[data-fila]")).hasSize(3);
        Element bomba = hoja.select("[data-fila]").get(0);
        assertThat(bomba.text()).contains("Bomba de infusión").contains("SN-2").contains("HV-CLE-0002");
        assertThat(bomba.hasClass("cr-fuera")).isTrue();
        assertThat(bomba.selectFirst("[data-proximo]").text()).isEqualTo("Fuera de servicio");
        assertThat(bomba.select("[data-marca=PROGRAMADO]")).isEmpty();
        assertThat(bomba.select("[data-marca=REALIZADO] .cr-cuadro--realizado")).hasSize(1);
        Element balanza = hoja.select("[data-fila]").get(1);
        assertThat(balanza.hasClass("cr-fuera")).isFalse();
        assertThat(balanza.selectFirst("[data-proximo]").text()).isEqualTo("18 abr 2027");
        assertThat(balanza.select("td.cr-celda")).hasSize(13);
        assertThat(balanza.select("[data-marca=PROGRAMADO] .cr-cuadro--programado")).hasSize(2);
        assertThat(balanza.select("[data-marca=NADA] i")).isEmpty();
    }

    @Test
    @DisplayName("el pie dice de dónde sale la fecha: la del servicio más la periodicidad")
    void elPie() {
        assertThat(pintar("E", null).selectFirst("[data-nota]").text())
                .contains("el 18 oct 2026").contains("periodicidad semestral").contains("el 18 abr 2027");
    }
}
