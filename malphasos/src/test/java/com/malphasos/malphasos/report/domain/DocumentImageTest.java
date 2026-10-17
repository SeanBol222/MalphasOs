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
}
