package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** El catálogo metrológico sembrado por {@code V10}, leído por su código. Ninguna prueba lo borra. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MetrologyCatalogAdapterTest {

    @Autowired private MetrologyCatalogAdapter adaptador;

    @Test
    @DisplayName("una magnitud sembrada trae los simbolos de sus unidades, y una que no existe no trae nada")
    void porSuCodigo() {
        assertThat(adaptador.unitsOf("humedad_relativa")).hasValueSatisfying(u -> assertThat(u).contains("%HR", "%"));
        assertThat(adaptador.unitsOf("presion")).hasValueSatisfying(u -> assertThat(u).contains("mmHg").doesNotContain("°C"));
        assertThat(adaptador.unitsOf("Presion")).isEmpty();
    }
}
