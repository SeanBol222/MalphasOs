package com.malphasos.malphasos.dataload.infrastructure.output.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El esquema que valida el backend es el publicado. La imagen del backend se construye sin ver
 * {@code contracts/}, así que lleva una copia en el classpath; esta prueba es lo que impide que la copia
 * y el original se separen sin que nadie lo note.
 */
class CargaSchemaCopyTest {

    @Test
    @DisplayName("la copia del esquema en el classpath es identica a la de contracts/carga")
    void laCopiaEsElPublicado() throws IOException {
        byte[] publicado = Files.readAllBytes(EjemplosDeCarga.ruta(EjemplosDeCarga.ESQUEMA));
        byte[] copia;
        try (InputStream entrada = getClass().getResourceAsStream(JsonSchemaLoadFormatAdapter.ESQUEMA)) {
            assertThat(entrada).describedAs("El esquema tiene que estar en el classpath").isNotNull();
            copia = entrada.readAllBytes();
        }

        assertThat(copia)
                .describedAs("Si cambia contracts/carga/malphasos-carga-1.schema.json, se copia a"
                        + " malphasos/src/main/resources/carga/")
                .isEqualTo(publicado);
    }
}
