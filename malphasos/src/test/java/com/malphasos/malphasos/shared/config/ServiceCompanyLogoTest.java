package com.malphasos.malphasos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El logo de la empresa sale de su directorio de configuración, y un logo roto no deja arrancar. */
class ServiceCompanyLogoTest {

    @TempDir Path directorio;

    @Test
    @DisplayName("sin logo configurado no hay logo, y no es un error")
    void sinLogo() {
        assertThat(ServiceCompanyLogo.leer(directorio.toString(), null)).isNull();
        assertThat(ServiceCompanyLogo.leer(directorio.toString(), "  ")).isNull();
    }

    @Test
    @DisplayName("el logo se resuelve contra el directorio y viaja como data: URI con su tipo")
    void comoDataUri() throws IOException {
        byte[] contenido = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Files.write(directorio.resolve("logo.PNG"), contenido);

        assertThat(ServiceCompanyLogo.leer(directorio.toString(), "logo.PNG"))
                .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(contenido));
    }

    @Test
    @DisplayName("un SVG viaja como image/svg+xml")
    void svg() throws IOException {
        Files.writeString(directorio.resolve("logo.svg"), "<svg/>");

        assertThat(ServiceCompanyLogo.leer(directorio.toString(), "logo.svg")).startsWith("data:image/svg+xml;base64,");
    }

    @Test
    @DisplayName("un logo configurado que no existe tumba el arranque diciendo dónde lo buscó")
    void noExiste() {
        assertThatThrownBy(() -> ServiceCompanyLogo.leer(directorio.toString(), "logo.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(directorio.resolve("logo.png").toString());
    }

    @Test
    @DisplayName("solo PNG, JPG o SVG")
    void tipoNoAdmitido() throws IOException {
        Files.writeString(directorio.resolve("logo.gif"), "GIF89a");

        assertThatThrownBy(() -> ServiceCompanyLogo.leer(directorio.toString(), "logo.gif"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PNG, JPG o SVG");
    }

    @Test
    @DisplayName("el tope es medio megabyte: lo justo entra y un byte más no")
    void tamano() throws IOException {
        Files.write(directorio.resolve("justo.jpg"), new byte[(int) ServiceCompanyLogo.TAMANO_MAXIMO]);
        Files.write(directorio.resolve("grande.jpg"), new byte[(int) ServiceCompanyLogo.TAMANO_MAXIMO + 1]);

        assertThat(ServiceCompanyLogo.leer(directorio.toString(), "justo.jpg")).startsWith("data:image/jpeg;base64,");
        assertThatThrownBy(() -> ServiceCompanyLogo.leer(directorio.toString(), "grande.jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pesa");
    }
}
