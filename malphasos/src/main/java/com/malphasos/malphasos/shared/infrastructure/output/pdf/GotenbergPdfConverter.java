package com.malphasos.malphasos.shared.infrastructure.output.pdf;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.DoubleFunction;
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
 * Convierte HTML en PDF con Gotenberg: Chromium sin pantalla en un contenedor interno, sin puertos
 * publicados. Lo usan los dos documentos que el servidor entrega en PDF, el informe de un reporte
 * (2026-10-17) y la hoja de vida de un equipo (2026-10-18), y por eso vive aquí y no en uno de los dos
 * módulos: {@code report} ya depende de {@code equipment}, y al revés habría un ciclo.
 *
 * <p>La hoja carta la pone cada plantilla con {@code @page}; aquí solo se pide que la respete y que
 * imprima los fondos, sin los cuales las bandas saldrían en blanco.
 */
@Component
public class GotenbergPdfConverter {

    private final RestClient gotenberg;

    /**
     * Con cliente propio: Spring Boot 4 sacó el {@code RestClient.Builder} autoconfigurado a un módulo que
     * este proyecto no usa. Y con plazos: sin ellos, un Gotenberg colgado deja colgada la petición del PDF.
     */
    public GotenbergPdfConverter(@Value("${app.gotenberg.url}") String url) {
        JdkClientHttpRequestFactory conexion = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        conexion.setReadTimeout(Duration.ofSeconds(60));
        this.gotenberg = RestClient.builder().baseUrl(url).requestFactory(conexion).build();
    }

    /**
     * La página entera es la plantilla, sin márgenes.
     *
     * @throws PdfConverterUnavailableException si el conversor no responde o falla
     */
    public byte[] convert(String html) {
        return enviar(partes(html));
    }

    /**
     * Con un pie que Chromium repite en cada página, en el margen de abajo: es el único sitio donde se
     * conoce el número de página y el total —las clases {@code pageNumber} y {@code totalPages}—, que
     * desde la página no se pueden calcular.
     *
     * @param margenInferior el alto del margen donde va el pie, en pulgadas; la plantilla tiene que
     *     restárselo a sus hojas
     * @throws PdfConverterUnavailableException si el conversor no responde o falla
     */
    public byte[] convert(String html, String pie, double margenInferior) {
        MultiValueMap<String, Object> partes = partes(html);
        partes.add("files", archivo("footer.html", pie));
        partes.add("marginBottom", String.format(Locale.ROOT, "%.2f", margenInferior));

        return enviar(partes);
    }

    /**
     * Con el cierre —las firmas— al pie de la última página en que cae (2026-11-21). Convierte, mide en
     * el PDF cuánto le sobra debajo y, si sobra, convierte otra vez con ese hueco delante: ver
     * {@link ClosingAtBottom}. Con una página basta la primera, porque lo resuelve el CSS.
     *
     * @param html la página, dado cuántos píxeles hay que bajar el cierre; con cero, donde lo deje el CSS
     * @throws PdfConverterUnavailableException si el conversor no responde o falla
     */
    public byte[] convertWithClosingAtBottom(DoubleFunction<String> html, ClosingAtBottom.Spec cierre) {
        return ClosingAtBottom.acomodar(bajada -> convert(html.apply(bajada)), cierre);
    }

    /** Lo mismo, con el pie de {@link #convert(String, String, double)} en cada página. */
    public byte[] convertWithClosingAtBottom(
            DoubleFunction<String> html, String pie, double margenInferior, ClosingAtBottom.Spec cierre) {
        return ClosingAtBottom.acomodar(bajada -> convert(html.apply(bajada), pie, margenInferior), cierre);
    }

    private static MultiValueMap<String, Object> partes(String html) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        // Gotenberg exige que la página se llame así.
        partes.add("files", archivo("index.html", html));
        // El tamaño lo decide la plantilla. Hoy la hoja por defecto de Gotenberg también es carta, así que
        // quitarlo no cambia nada, pero así no depende de un valor por defecto ajeno.
        partes.add("preferCssPageSize", "true");
        partes.add("printBackground", "true");

        return partes;
    }

    private static ByteArrayResource archivo(String nombre, String contenido) {
        return new ByteArrayResource(contenido.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return nombre;
            }
        };
    }

    private byte[] enviar(MultiValueMap<String, Object> partes) {
        try {
            return gotenberg.post()
                    .uri("/forms/chromium/convert/html")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException caido) {
            throw new PdfConverterUnavailableException(caido);
        }
    }
}
