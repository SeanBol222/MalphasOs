package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.report.domain.serviceReport.DocumentImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Una imagen que un informe congela: el logo o la firma (V37 y V38, 2026-10-17). */
class DocumentImageTest {

    private static final byte[] SVG = "<svg/>".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("la huella es el SHA-256 de sus bytes: igual para dos iguales, distinta si cambia uno")
    void huella() {
        // Calculado fuera de Java, con sha256sum: si la huella cambiara de algoritmo o de forma, esto cae.
        assertThat(new DocumentImage(SVG, "image/svg+xml").huella())
                .isEqualTo("d4dc56669143034f31aa309635d4113d9ad76a02b1739da22c965ed2049be9e6")
                .isEqualTo(new DocumentImage(SVG.clone(), "image/svg+xml").huella())
                .isNotEqualTo(new DocumentImage("<svg />".getBytes(StandardCharsets.UTF_8), "image/svg+xml").huella());
    }

    @Test
    @DisplayName("viaja como data: URI con su tipo")
    void dataUri() {
        assertThat(new DocumentImage(SVG, "image/svg+xml").dataUri())
                .isEqualTo("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(SVG));
    }

    @Test
    @DisplayName("vacía no es una imagen, y no comparte sus bytes")
    void vaciaYCopia() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DocumentImage(new byte[0], "image/png"));
        byte[] original = SVG.clone();
        DocumentImage imagen = new DocumentImage(original, "image/svg+xml");
        original[0] = 'X';
        imagen.contenido()[1] = 'X';

        assertThat(imagen.contenido()).isEqualTo(SVG);
    }

    @Test
    @DisplayName("una firma es un PNG: se mira la cabecera de los bytes")
    void firmaPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1};

        assertThat(DocumentImage.firma(png)).isEqualTo(new DocumentImage(png, "image/png"));
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentImage.firma(SVG))
                .withMessageContaining("PNG");
        // Más corta que la cabecera: no puede ser un PNG.
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentImage.firma(new byte[] {(byte) 0x89, 'P'}))
                .withMessageContaining("PNG");
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentImage.firma(new byte[0]));
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentImage.firma(null));
    }

    @Test
    @DisplayName("una firma pesa como mucho 256 KB, y justo eso cabe")
    void firmaTamano() {
        byte[] justa = new byte[DocumentImage.TAMANO_MAXIMO_FIRMA];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, justa, 0, 8);
        byte[] pasada = java.util.Arrays.copyOf(justa, justa.length + 1);

        assertThat(DocumentImage.firma(justa).contenido()).hasSize(256 * 1024);
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentImage.firma(pasada))
                .withMessageContaining("máximo");
    }
}
