package com.malphasos.malphasos.company.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El logo del archivo de arranque sale de su directorio. Si es una imagen admitida lo decide
 * {@code CompanyLogo}, con la misma regla que para uno subido: ver {@code CompanyLogoTest}.
 */
class ServiceCompanyLogoTest {

    @TempDir Path directorio;

    @Test
    @DisplayName("sin logo configurado no hay logo, y no es un error")
    void sinLogo() {
        assertThat(ServiceCompanyLogo.leer(directorio.toString(), null)).isNull();
        assertThat(ServiceCompanyLogo.leer(directorio.toString(), "  ")).isNull();
    }

    @Test
    @DisplayName("el logo se resuelve contra el directorio y se lee entero")
    void seLee() throws IOException {
        byte[] contenido = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Files.write(directorio.resolve("logo.png"), contenido);

        assertThat(ServiceCompanyLogo.leer(directorio.toString(), "logo.png")).isEqualTo(contenido);
    }

    @Test
    @DisplayName("un logo configurado que no existe tumba la siembra diciendo dónde lo buscó")
    void noExiste() {
        assertThatThrownBy(() -> ServiceCompanyLogo.leer(directorio.toString(), "logo.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(directorio.resolve("logo.png").toString());
    }
}
