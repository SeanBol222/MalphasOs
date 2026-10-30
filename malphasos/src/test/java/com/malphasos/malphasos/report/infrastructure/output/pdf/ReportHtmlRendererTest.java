package com.malphasos.malphasos.report.infrastructure.output.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.report.application.services.serviceReport.sheet.ReportSheetComposer;
import com.malphasos.malphasos.report.application.sheet.UnInforme;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Lo que imprime el informe en PDF (2026-10-17, RF-17), leído en el HTML que Gotenberg convierte. Son los
 * casos de {@code documento-reporte.spec.ts}, que fijaban el documento cuando lo pintaba Angular: el
 * diseño se movió al servidor y sus reglas con él.
 *
 * <p>Con el mismo motor que Spring Boot configura —Thymeleaf sobre SpEL— y sin levantar el contexto.
 */
class ReportHtmlRendererTest {

    private static final ReportHtmlRenderer RENDERER = new ReportHtmlRenderer(motor(), "https://malphasos.example/");

    private static final DocumentImage PNG =
            new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1}, "image/png");
    private static final DocumentImage OTRO_PNG =
            new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 2}, "image/png");

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

    private static Document pintar(UnInforme informe) {
        return Jsoup.parse(RENDERER.render(ReportSheetComposer.compose(informe.vista())));
    }

    private static String texto(Document hoja, String selector) {
        Element elemento = hoja.selectFirst(selector);

        return elemento == null ? null : elemento.text();
    }

    private static List<String> celdas(Element donde) {
        return donde.select("tbody td").stream().map(Element::text).toList();
    }

    @Test
    @DisplayName("la banda dice el número, el título único, la orden y el equipo")
    void laBanda() {
        Document hoja = pintar(new UnInforme());

        assertThat(texto(hoja, "[data-numero]")).isEqualTo("Informe N.º 1DC6F5");
        assertThat(texto(hoja, "[data-numero-orden]")).isEqualTo("Orden N.º 4F2A9C");
        assertThat(texto(hoja, "h1")).isEqualTo("Informe de mantenimiento");
        assertThat(texto(hoja, "[data-equipo]"))
                .isEqualTo("Tensiómetro · Welch Allyn ProBP 2400 · Serie SN-0001 · HV-HCE-0007");
    }

    @Test
    @DisplayName("un borrador lleva la marca, la fecha programada y el próximo servicio con la periodicidad")
    void unBorrador() {
        Document hoja = pintar(new UnInforme());

        assertThat(hoja.select("[data-borrador]")).hasSize(1);
        assertThat(texto(hoja, "[data-fecha]")).isEqualTo("15 oct 2026");
        assertThat(texto(hoja, "[data-proximo]")).isEqualTo("15 ene 2027");
        assertThat(texto(hoja, "[data-resultado]")).isEqualTo("Sin registrar");
    }

    @Test
    @DisplayName("uno cerrado lleva la fecha del cierre y no lleva la marca de borrador")
    void unCerrado() {
        Document hoja = pintar(new UnInforme().cerrado(ServiceResult.OPERATIVO));

        assertThat(hoja.select("[data-borrador]")).isEmpty();
        assertThat(texto(hoja, "[data-fecha]")).isEqualTo("20 oct 2026");
        assertThat(texto(hoja, "[data-proximo]")).isEqualTo("20 ene 2027");
    }

    @ParameterizedTest(name = "{0} se imprime «{1}»")
    @CsvSource({
        "OPERATIVO, Apto para el servicio",
        "OPERATIVO_CON_RESTRICCIONES, Apto para el servicio",
        "FUERA_DE_SERVICIO, No apto para el servicio"
    })
    @DisplayName("tres resultados guardados, dos impresos: la restricción va en las observaciones")
    void elResultado(ServiceResult resultado, String impreso) {
        assertThat(texto(pintar(new UnInforme().cerrado(resultado)), "[data-resultado]")).isEqualTo(impreso);
    }

    @Test
    @DisplayName("las fichas del cliente y del equipo dicen lo que trae el informe, campo por campo")
    void lasFichas() {
        Map<String, String> fichas = pintar(new UnInforme()).select("dt").stream()
                .collect(Collectors.toMap(Element::text, dt -> dt.nextElementSibling().text()));

        assertThat(fichas).containsEntry("Razón social · NIT", "Hospital Central · 900123456")
                .containsEntry("Sede · Ciudad", "Sede Norte · Bogotá")
                .containsEntry("Dirección", "Calle 10 # 20 - 30")
                .containsEntry("Supervisor responsable", "Carla Ruiz")
                .containsEntry("Teléfono", "3001112233")
                .containsEntry("Correo", "compras@hospital.co")
                .containsEntry("Marca · Modelo", "Welch Allyn · ProBP 2400")
                .containsEntry("Placa · Código interno", "INV-77 · MON-03")
                .containsEntry("Área", "Urgencias")
                .containsEntry("Tecnología · Uso", "Electrónica · Medir la presión arterial")
                .containsEntry("Riesgo", "Clase IIa")
                .containsEntry("Alimentación", "AC · 110 V · 60 Hz");
    }

    @Test
    @DisplayName("sin supervisor, sin teléfono y sin código interno, la raya")
    void laRaya() {
        UnInforme informe = new UnInforme();
        informe.responsables = List.of();
        Document hoja = pintar(informe);

        assertThat(texto(hoja, "[data-supervisor]")).isEqualTo("—");
    }

    @Test
    @DisplayName("marca el tipo de servicio de la orden, y la verificación metrológica sola si hay lecturas")
    void lasCasillas() {
        Document sinLecturas = pintar(new UnInforme());
        Document conLecturas = pintar(new UnInforme().conLecturas());

        assertThat(marcadas(sinLecturas)).containsExactly("Preventivo");
        assertThat(marcadas(conLecturas)).containsExactly("Preventivo", "Verificación metrológica");
        assertThat(texto(sinLecturas, "[data-casilla=Preventivo]")).isEqualTo("■ Preventivo");
        assertThat(texto(sinLecturas, "[data-casilla=Correctivo]")).isEqualTo("□ Correctivo");
    }

    @Test
    @DisplayName("un correctivo en garantía marca las dos casillas")
    void correctivoEnGarantia() {
        UnInforme informe = new UnInforme();
        informe.tipoServicio = "CORRECTIVO";
        informe.garantiaDeLaOrden = true;

        assertThat(marcadas(pintar(informe))).containsExactly("Correctivo", "Garantía");
    }

    private static List<String> marcadas(Document hoja) {
        return hoja.select("[data-casilla].rs-marcada").stream().map(e -> e.attr("data-casilla")).toList();
    }

    @Test
    @DisplayName("sin lecturas la verificación lo dice; con ellas cuenta las conformes entre las que tienen tolerancia")
    void laVerificacion() {
        assertThat(texto(pintar(new UnInforme()), "[data-verificacion]")).isEqualTo("Sin verificación");
        assertThat(texto(pintar(new UnInforme().conLecturas()), "[data-verificacion]")).isEqualTo("1 de 2 conformes");
    }

    @Test
    @DisplayName("las lecturas sin tolerancia no cuentan como conformes ni como no conformes: no se sabe")
    void sinToleranciaNoSeSabe() {
        UnInforme informe = new UnInforme();
        // La tolerancia es la de la verificación en el documento desde V51: aquí, ninguna.
        informe.verificaciones.set(0, new ReportDocument.Verificacion(UnInforme.VERIFICACION, "Presión", "mmHg", 3,
                null, informe.verificaciones.getFirst().puntos()));
        informe.lecturas.add(VerificationReading.rehydrate(UUID.randomUUID(), UnInforme.VERIFICACION,
                UnInforme.PUNTO, 1, new BigDecimal("100"), new BigDecimal("100.3"), true));
        Document hoja = pintar(informe);

        assertThat(texto(hoja, "[data-verificacion]")).isEqualTo("1 lectura");
        assertThat(texto(hoja, "[data-tolerancia]")).isEqualTo("sin tolerancia");
        assertThat(celdas(hoja.selectFirst("[data-medida]"))).endsWith("—");
        assertThat(hoja.select("rect[data-banda]")).isEmpty();
    }

    @Test
    @DisplayName("el ingreso, los accesorios y quien recibe salen como se registraron")
    void elIngreso() {
        UnInforme informe = new UnInforme();
        informe.accesorios.add(new ReportAccessory(UUID.randomUUID(), 1, "Brazalete", false, "Roto", true));
        informe.recibe = new Handover("Luz Gómez", "52123456", "Jefe de enfermería");
        Document hoja = pintar(informe);

        assertThat(hoja.select(".rs-tabla tbody tr").stream().map(Element::text).toList()).containsExactly(
                "Estado de la estructura física del equipo Conforme —",
                "Estado operativo del equipo No conforme No enciende la pantalla",
                "Accesorio: Brazalete No conforme Roto");
        assertThat(hoja.select("[data-accesorio]")).hasSize(1);
        assertThat(texto(hoja, "[data-firma-recibe]")).isEqualTo("Luz Gómez · C.C. 52123456");
        assertThat(hoja.selectFirst("[data-firma-recibe]").nextElementSibling().text())
                .isEqualTo("Recibe · Jefe de enfermería");
    }

    @Test
    @DisplayName("sin registrar, el ingreso lo dice y los accesorios dicen que no tiene")
    void sinRegistrar() {
        UnInforme informe = new UnInforme();
        informe.ingresoFisico = null;
        informe.ingresoOperativo = null;
        Document hoja = pintar(informe);

        assertThat(hoja.select(".rs-tabla tbody tr").stream().map(Element::text).toList()).containsExactly(
                "Estado de la estructura física del equipo Sin registrar —",
                "Estado operativo del equipo Sin registrar —",
                "Accesorios No tiene —");
        assertThat(hoja.select("[data-accesorio]")).isEmpty();
        assertThat(texto(hoja, "[data-firma-recibe]")).isEqualTo("—");
    }

    @Test
    @DisplayName("los pasos del protocolo: ✓ hecho, N/A con su nota, en blanco si quedó pendiente")
    void elProtocolo() {
        UnInforme informe = new UnInforme()
                .conPaso("Limpieza", StepState.HECHO, null)
                .conPaso("Batería", StepState.NO_APLICA, "No tiene")
                .conPaso("Cables", StepState.PENDIENTE, null);
        Document hoja = pintar(informe);

        assertThat(hoja.select("[data-protocolo] li").stream().map(Element::text).toList())
                .containsExactly("✓ Limpieza", "N/A Batería — No tiene", "Cables");
        // Con pasos, el texto libre es trabajo adicional.
        assertThat(texto(hoja, "[data-relato]")).contains("Trabajo adicional. Limpieza general");
    }

    @Test
    @DisplayName("los pasos salen en el orden del documento, no en el de las filas del reporte (V51)")
    void elProtocoloEnElOrdenDelDocumento() {
        UnInforme informe = new UnInforme()
                .conPaso("Limpieza", StepState.HECHO, null)
                .conPaso("Cables", StepState.HECHO, null);
        java.util.Collections.reverse(informe.pasos);

        assertThat(pintar(informe).select("[data-protocolo] li").stream().map(Element::text).toList())
                .containsExactly("✓ Limpieza", "✓ Cables");
    }

    @Test
    @DisplayName("lo escrito a mano sale en su orden y solo lo que tiene texto; sin pasos, son los procedimientos")
    void elRelato() {
        UnInforme informe = new UnInforme();
        informe.fallaReportada = "No enciende";
        informe.diagnostico = "Fusible";
        informe.repuestos = "Fusible de 2 A";
        Document hoja = pintar(informe);

        assertThat(hoja.select("[data-relato] p").stream().map(Element::text).toList()).containsExactly(
                "Falla reportada. No enciende", "Diagnóstico. Fusible", "Procedimientos. Limpieza general",
                "Repuestos. Fusible de 2 A");
        assertThat(hoja.select("[data-protocolo]")).isEmpty();
        assertThat(texto(hoja, "[data-observaciones]")).isEqualTo("Observaciones. Ninguna.");
    }

    @Nested
    @DisplayName("Las firmas")
    class LasFirmas {

        @Test
        @DisplayName("con las dos firmas, cada línea lleva la suya; el profesional es el ingeniero del informe")
        void lasDos() {
            UnInforme informe = new UnInforme();
            informe.firmaIngeniero = PNG;
            informe.firmaRecibe = OTRO_PNG;
            Document hoja = pintar(informe);

            assertThat(hoja.selectFirst("[data-firma-ingeniero]").attr("src")).isEqualTo(PNG.dataUri());
            assertThat(hoja.selectFirst("[data-firma-recibe-trazo]").attr("src")).isEqualTo(OTRO_PNG.dataUri());
            assertThat(texto(hoja, "[data-firma-profesional]")).isEqualTo("Ana Ruiz");
        }

        @Test
        @DisplayName("sin firmas, las líneas quedan para firmar a mano; sin ingeniero, la raya")
        void sinFirmas() {
            UnInforme informe = new UnInforme();
            informe.ingeniero = null;
            Document hoja = pintar(informe);

            assertThat(hoja.select("[data-firma-ingeniero], [data-firma-recibe-trazo]")).isEmpty();
            assertThat(texto(hoja, "[data-firma-profesional]")).isEqualTo("—");
        }
    }

    @Nested
    @DisplayName("Los textos de la empresa")
    class LaEmpresa {

        @Test
        @DisplayName("la aclaración, la garantía, el pie y el membrete son los que la empresa configuró")
        void configurados() {
            UnInforme informe = new UnInforme();
            informe.logo = PNG;
            Document hoja = pintar(informe);

            assertThat(texto(hoja, "[data-aclaracion]"))
                    .isEqualTo("Aclaración del proceso. Mantenimiento realizado bajo el protocolo de la empresa.");
            assertThat(texto(hoja, "[data-garantia]")).isEqualTo("Garantía. " + UnInforme.GARANTIA);
            assertThat(texto(hoja, "[data-pie]"))
                    .isEqualTo("FT-002 · Versión 1 · Carrera 1 N.º 2-3, Ciudad de Ejemplo · 300 000 0000");
            assertThat(texto(hoja, ".rs-logo")).isEqualTo("Servicios de Ejemplo S.A.S.");
            assertThat(hoja.select("img.rs-escudo")).hasSize(1);
            assertThat(hoja.selectFirst(".rs-logo img").attr("src")).isEqualTo(PNG.dataUri());
        }

        @Test
        @DisplayName("sin textos ni logo —o con los textos en blanco— no hay aclaración, garantía, imagen ni marca de agua")
        void sinConfigurar() {
            UnInforme informe = new UnInforme();
            // En blanco cuenta como sin configurar: un rótulo «Garantía.» sin texto no dice nada.
            informe.empresa = new ReportDocument.Empresa("Servicios de Ejemplo S.A.S.", null, null, null, null,
                    "  ", " ", null, null);
            Document hoja = pintar(informe);

            assertThat(hoja.select("[data-aclaracion], [data-garantia], img")).isEmpty();
            assertThat(texto(hoja, "[data-pie]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("La página 2: la verificación metrológica")
    class LaPaginaDos {

        @Test
        @DisplayName("con lecturas hay página 2: la trazabilidad, la tabla en su orden, la gráfica y la garantía")
        void conLecturas() {
            Document hoja = pintar(new UnInforme().conLecturas());
            Element pagina = hoja.selectFirst("[data-pagina=2]");

            assertThat(pagina).isNotNull();
            assertThat(celdas(pagina.selectFirst("table"))).containsExactly(
                    "Manómetro patrón Fluke 700G · Presión", "M-1 BB-PAT-003", "0 a 300 mmHg 0,1 mmHg", "CAL-1",
                    "30 abr 2026 vence 30 abr 2099", "Metrología Andina");
            // Llegan al revés y salen por su número; con tres datos por punto, el número va en el registro.
            assertThat(celdas(pagina.selectFirst("[data-medida]"))).containsExactly(
                    "100 mmHg · 1", "100", "100,2", "+0,2", "Sí",
                    "100 mmHg · 2", "100", "100,9", "+0,9", "No");
            assertThat(pagina.select("[data-lectura].rs-fuera")).hasSize(1);
            assertThat(pagina.select("circle[data-fuera]")).hasSize(1);
            assertThat(pagina.select("rect[data-banda]")).hasSize(1);
            assertThat(texto(hoja, "[data-tolerancia]")).isEqualTo("tolerancia ±0,5 mmHg");
            assertThat(pagina.text()).contains("1 de 2 lecturas conformes", "Contra: Manómetro patrón",
                    "Página 2 de 2");
            // La garantía cierra el informe: va solo en la segunda hoja, sin repetirse.
            assertThat(hoja.select("[data-garantia]")).hasSize(1);
            assertThat(pagina.select("[data-garantia]")).hasSize(1);
        }

        @Test
        @DisplayName("sin lecturas el informe es de una hoja, y la garantía lo cierra en ella")
        void sinLecturas() {
            Document hoja = pintar(new UnInforme());

            assertThat(hoja.select("[data-pagina=2]")).isEmpty();
            assertThat(hoja.text()).contains("Página 1 de 1");
            assertThat(hoja.select("[data-pagina=1] [data-garantia]")).hasSize(1);
        }

        @Test
        @DisplayName("un patrón usado en dos verificaciones sale una vez, con las dos magnitudes")
        void unPatronDosVerificaciones() {
            UUID humedad = UUID.randomUUID();
            UnInforme informe = new UnInforme().conLecturas();
            informe.verificaciones.add(new ReportDocument.Verificacion(humedad, "Humedad", "%HR", 1,
                    new BigDecimal("2"), List.of()));
            // Primero la de humedad: las medidas salen en el orden en que el tipo las declara, no en el
            // de las lecturas.
            informe.lecturas.addFirst(VerificationReading.rehydrate(UUID.randomUUID(), humedad, null, 1,
                    new BigDecimal("50"), new BigDecimal("51"), true));
            informe.patrones.add(new VerificationStandard(UUID.randomUUID(), humedad, UnInforme.PATRON.id(), true));
            Document hoja = pintar(informe);

            assertThat(hoja.select("[data-trazabilidad]")).hasSize(1);
            assertThat(hoja.selectFirst("[data-trazabilidad] .rs-debajo").text())
                    .isEqualTo("Fluke 700G · Presión y Humedad");
            // Las medidas en el orden en que el tipo las declara, y sin punto la lectura es «X1».
            assertThat(hoja.select("[data-medida] h2").stream().map(Element::text).toList())
                    .containsExactly("Presión · mmHg", "Humedad · %HR");
            assertThat(celdas(hoja.select("[data-medida]").get(1)).getFirst()).isEqualTo("X1");
        }

        @Test
        @DisplayName("sin patrones registrados, la trazabilidad lo dice")
        void sinPatrones() {
            UnInforme informe = new UnInforme().conLecturas();
            informe.patrones.clear();

            assertThat(pintar(informe).select("[data-sin-patrones]")).hasSize(1);
        }
    }

    @Test
    @DisplayName("el QR lleva a este mismo informe en la aplicación, en la dirección pública del frontend")
    void elQr() {
        Element qr = pintar(new UnInforme()).selectFirst("[data-qr]");

        assertThat(qr.attr("aria-label"))
                .isEqualTo("Código QR: https://malphasos.example/reportes/" + UnInforme.ID + "/documento");
        assertThat(qr.selectFirst("path").attr("d")).startsWith("M0 0h1v1h-1z");
    }

    @Test
    @DisplayName("las coordenadas de la gráfica llevan punto decimal: con coma el SVG no se dibuja")
    void coordenadas() {
        Element circulo = pintar(new UnInforme().conLecturas()).selectFirst("circle");

        assertThat(circulo.attr("cx")).matches("\\d+\\.\\d{2}");
        assertThat(circulo.attr("cy")).matches("\\d+\\.\\d{2}");
    }

    @Test
    @DisplayName("la fuente de la marca va dentro del documento: Gotenberg no sale a internet")
    void laFuente() {
        String html = RENDERER.render(ReportSheetComposer.compose(new UnInforme().vista()));

        assertThat(html).contains("src: url(data:font/woff2;base64,d09GMg");
        assertThat(html).doesNotContain("fonts.googleapis");
    }

    @Test
    @DisplayName("es un documento: no tiene un solo control")
    void sinControles() {
        assertThat(pintar(new UnInforme().conLecturas()).select("input, textarea, select, button")).isEmpty();
    }
}
