package com.malphasos.malphasos.company.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El logo de la empresa (2026-10-17): su tipo sale de sus bytes, no de quien lo sube. */
class CompanyLogoTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1};

    private static byte[] texto(String contenido) {
        return contenido.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("PNG, JPG y SVG se reconocen por sus bytes")
    void tipos() {
        assertThat(CompanyLogo.of(PNG).tipo()).isEqualTo("image/png");
        assertThat(CompanyLogo.of(JPEG).tipo()).isEqualTo("image/jpeg");
        assertThat(CompanyLogo.of(texto("<svg xmlns=\"http://www.w3.org/2000/svg\"/>")).tipo())
                .isEqualTo("image/svg+xml");
    }

    @Test
    @DisplayName("un SVG con declaración XML y espacios delante también es SVG")
    void svgConDeclaracion() {
        assertThat(CompanyLogo.of(texto("\n  <?xml version=\"1.0\"?>\n<!-- logo -->\n<SVG/>")).tipo())
                .isEqualTo("image/svg+xml");
    }

    @Test
    @DisplayName("lo que no es PNG, JPG ni SVG no entra, aunque se llame logo.png")
    void otroTipo() {
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyLogo.of(texto("GIF89a")))
                .withMessageContaining("PNG, JPG o SVG");
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyLogo.of(texto("<html><body/></html>")));
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyLogo.of(texto("hola <svg")));
    }

    @Test
    @DisplayName("vacío no es un logo")
    void vacio() {
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyLogo.of(new byte[0]));
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyLogo.of(null));
    }

    @Test
    @DisplayName("el tope es medio megabyte: lo justo entra y un byte más no")
    void tamano() {
        byte[] justo = Arrays.copyOf(PNG, CompanyLogo.TAMANO_MAXIMO);
        byte[] grande = Arrays.copyOf(PNG, CompanyLogo.TAMANO_MAXIMO + 1);

        assertThat(CompanyLogo.of(justo).contenido()).hasSize(CompanyLogo.TAMANO_MAXIMO);
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyLogo.of(grande)).withMessageContaining("pesa");
    }

    @Test
    @DisplayName("rehidratado con un tipo que no casa con sus bytes, falla: algo lo alteró por fuera")
    void tipoGuardadoQueNoCasa() {
        assertThat(new CompanyLogo(PNG, "image/png").tipo()).isEqualTo("image/png");
        assertThatIllegalArgumentException().isThrownBy(() -> new CompanyLogo(PNG, "image/jpeg"))
                .withMessageContaining("image/jpeg");
    }

    @Test
    @DisplayName("viaja como data: URI con su tipo")
    void dataUri() {
        assertThat(CompanyLogo.of(PNG).dataUri())
                .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG));
    }

    @Test
    @DisplayName("no comparte sus bytes: cambiar el arreglo original o el devuelto no lo altera")
    void copiaDefensiva() {
        byte[] original = PNG.clone();
        CompanyLogo logo = CompanyLogo.of(original);
        original[9] = 99;
        logo.contenido()[9] = 99;

        assertThat(logo.contenido()).isEqualTo(PNG);
        assertThat(logo).isEqualTo(CompanyLogo.of(PNG));
    }

    @Test
    @DisplayName("la huella es el SHA-256 de sus bytes: igual para dos logos iguales, distinta si cambia uno")
    void huella() {
        assertThat(CompanyLogo.of(PNG).huella())
                .hasSize(64)
                .matches("[0-9a-f]+")
                .isEqualTo(CompanyLogo.of(PNG.clone()).huella())
                .isNotEqualTo(CompanyLogo.of(JPEG).huella());
        // Calculado fuera de Java, con sha256sum: si la huella cambiara de algoritmo o de forma, esto cae.
        assertThat(CompanyLogo.of(texto("<svg/>")).huella())
                .isEqualTo("d4dc56669143034f31aa309635d4113d9ad76a02b1739da22c965ed2049be9e6");
    }
}
