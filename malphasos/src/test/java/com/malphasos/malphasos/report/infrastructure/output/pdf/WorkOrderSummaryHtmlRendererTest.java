package com.malphasos.malphasos.report.infrastructure.output.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderSummaryComposer;
import com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
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

/** Lo que imprime el resumen de una orden (2026-10-31), leído en el HTML que Gotenberg convierte. */
class WorkOrderSummaryHtmlRendererTest {

    private static final WorkOrderSummaryHtmlRenderer RENDERER =
            new WorkOrderSummaryHtmlRenderer(motor(), "https://malphasos.example/");
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
        WorkOrderSummarySheet hoja = WorkOrderSummaryComposer.compose(UnaOrdenAtendida.ORDEN,
                UnaOrdenAtendida.documento(LocalDate.of(2026, 10, 18)), UnaOrdenAtendida.reportes(), empresa, logo);

        return Jsoup.parse(RENDERER.render(hoja));
    }

    @Test
    @DisplayName("la banda dice la empresa con su logo, la orden, el cliente y el ingeniero")
    void laBanda() {
        Document hoja = pintar("Empresa de Prueba", LOGO);

        assertThat(hoja.selectFirst("[data-empresa]").text()).isEqualTo("Empresa de Prueba");
        assertThat(hoja.selectFirst(".ro-empresa img").attr("src")).isEqualTo(LOGO.dataUri());
        assertThat(hoja.selectFirst("[data-servicio]").text())
                .isEqualTo("Orden N.º 1DC6F5 · Preventivo anual · Ejecutada el 18 oct 2026");
        assertThat(hoja.selectFirst("[data-cliente]").text())
                .contains("Clínica de Ejemplo S.A.S.").contains("Ingeniero: Ingeniera de Prueba");
        assertThat(hoja.title()).isEqualTo("Resumen de la orden N.º 1DC6F5");
    }

    @Test
    @DisplayName("sin empresa configurada no imprime la línea de la empresa")
    void sinEmpresa() {
        assertThat(pintar(null, null).selectFirst(".ro-empresa")).isNull();
    }

    @Test
    @DisplayName("el QR lleva a la ficha de la orden en el frontend")
    void elQr() {
        assertThat(pintar("E", null).selectFirst("[data-qr]").attr("aria-label"))
                .isEqualTo("Código QR: https://malphasos.example/ordenes/" + UnaOrdenAtendida.ORDEN);
    }

    @Test
    @DisplayName("los totales y una fila por equipo, con el fuera de servicio en el color de alerta y con más peso")
    void laTabla() {
        Document hoja = pintar("E", null);

        assertThat(hoja.select("[data-totales] strong").stream().map(Element::text).toList())
                .containsExactly("3", "2", "0", "1");
        assertThat(hoja.select("[data-totales] strong").get(3).hasClass("ro-alerta")).isTrue();
        List<Element> filas = hoja.select("[data-fila]");
        assertThat(filas).hasSize(3);
        assertThat(filas.get(0).select("td").stream().map(Element::text).toList()).containsExactly(
                "1", "Hospitalización", "Bomba de infusión", "Marca B Modelo B", "SN-2", "HV-CLE-0002", "CCCCCC",
                "16 oct 2026", "Fuera de servicio", "Luis Gómez");
        assertThat(filas.get(0).hasClass("ro-destacada")).isTrue();
        assertThat(filas.get(1).hasClass("ro-destacada")).isFalse();
    }

    @Test
    @DisplayName("sin nadie fuera de servicio, el cero no va en el color de alerta")
    void sinAlerta() {
        WorkOrderSummarySheet hoja = WorkOrderSummaryComposer.compose(UnaOrdenAtendida.ORDEN,
                UnaOrdenAtendida.documento(null), UnaOrdenAtendida.reportes().subList(0, 2), "E", null);

        assertThat(Jsoup.parse(RENDERER.render(hoja)).select("[data-totales] strong").get(3).hasClass("ro-alerta"))
                .isFalse();
    }

    private static Document conAprobacion(com.malphasos.malphasos.report.application.model.OrderApproval aprobacion) {
        WorkOrderSummarySheet hoja = WorkOrderSummaryComposer.compose(UnaOrdenAtendida.ORDEN,
                UnaOrdenAtendida.documento(LocalDate.of(2026, 10, 18)), UnaOrdenAtendida.reportes(), "E", null,
                com.malphasos.malphasos.report.domain.serviceReport.ServiceReport::getRecibe, aprobacion);

        return Jsoup.parse(RENDERER.render(hoja));
    }

    @Test
    @DisplayName("sin aprobación, el supervisor del cliente firma a mano con nombre y fecha (V54)")
    void sinAprobacion() {
        Document hoja = conAprobacion(null);

        assertThat(hoja.select("[data-firma-supervisor]")).isEmpty();
        assertThat(hoja.selectFirst("[data-firma-supervisor-nombre]").text()).isEqualTo("Supervisor del cliente");
        assertThat(hoja.selectFirst("[data-firma-supervisor-detalle]").text()).isEqualTo("Nombre y fecha");
    }

    @Test
    @DisplayName("aprobada, lleva a quien aprobó con su cargo, el día y su firma (V54)")
    void aprobada() {
        Document hoja = conAprobacion(new com.malphasos.malphasos.report.application.model.OrderApproval(
                "Luis Peña", "Ingeniero biomédico", LOGO, LocalDate.of(2026, 11, 2)));

        assertThat(hoja.selectFirst("[data-firma-supervisor]").attr("src")).isEqualTo(LOGO.dataUri());
        assertThat(hoja.selectFirst("[data-firma-supervisor-nombre]").text()).isEqualTo("Luis Peña");
        assertThat(hoja.selectFirst("[data-firma-supervisor-detalle]").text())
                .isEqualTo("Supervisor responsable · Ingeniero biomédico · Aprobó el 02 nov 2026");
    }
}
