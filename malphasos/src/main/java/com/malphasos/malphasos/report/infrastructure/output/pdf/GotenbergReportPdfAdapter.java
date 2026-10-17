package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.malphasos.malphasos.report.application.model.ReportSheet;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfPort;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfUnavailableException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * El informe en PDF con Gotenberg (2026-10-17, RF-17): Chromium sin pantalla en un contenedor interno, sin
 * puertos publicados. Se le manda el HTML de {@link ReportHtmlRenderer} y devuelve el PDF.
 *
 * <p>La hoja carta y los márgenes en cero los pone la plantilla con {@code @page}; aquí solo se pide que
 * los respete y que imprima los fondos, sin los cuales la banda saldría en blanco.
 */
@Component
public class GotenbergReportPdfAdapter implements ReportPdfPort {

    private final ReportHtmlRenderer renderer;
    private final RestClient gotenberg;

    /**
     * Con cliente propio: Spring Boot 4 sacó el {@code RestClient.Builder} autoconfigurado a un módulo que
     * este proyecto no usa. Y con plazos: sin ellos, un Gotenberg colgado deja colgada la petición del PDF.
     */
    public GotenbergReportPdfAdapter(ReportHtmlRenderer renderer, @Value("${app.gotenberg.url}") String url) {
        this.renderer = renderer;
        JdkClientHttpRequestFactory conexion = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        conexion.setReadTimeout(Duration.ofSeconds(60));
        this.gotenberg = RestClient.builder().baseUrl(url).requestFactory(conexion).build();
    }

    @Override
    public byte[] render(ReportSheet hoja) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("files", new ByteArrayResource(renderer.render(hoja).getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                // Gotenberg exige que se llame así.
                return "index.html";
            }
        });
        // El tamaño lo decide la plantilla. Hoy la hoja por defecto de Gotenberg también es carta, así que
        // quitarlo no cambia nada —su mutación sobrevive—, pero así no depende de un valor por defecto ajeno.
        partes.add("preferCssPageSize", "true");
        partes.add("printBackground", "true");

        try {
            return gotenberg.post()
                    .uri("/forms/chromium/convert/html")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException caido) {
            throw new ReportPdfUnavailableException(caido);
        }
    }
}
