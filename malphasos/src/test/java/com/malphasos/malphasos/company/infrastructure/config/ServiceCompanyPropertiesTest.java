package com.malphasos.malphasos.company.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * El archivo de la empresa es opcional desde el 2026-10-17: solo siembra el primer arranque. Hasta ese
 * día, sin nombre la aplicación no arrancaba.
 */
class ServiceCompanyPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ServiceCompanyProperties.class)
    static class Configuracion {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Configuracion.class);

    @Test
    @DisplayName("sin archivo arranca: la empresa se llena desde la pantalla")
    void sinNombre() {
        contexto.run(arrancado -> {
            assertThat(arrancado).hasNotFailed();
            assertThat(arrancado.getBean(ServiceCompanyProperties.class).getNombre()).isNull();
        });
    }

    @Test
    @DisplayName("con archivo lee lo que trae, y lo demás es opcional")
    void conNombre() {
        contexto.withPropertyValues("app.empresa.nombre=Servicios de Ejemplo", "app.empresa.codigo-hoja-de-vida=FT-001")
                .run(arrancado -> {
                    assertThat(arrancado).hasNotFailed();
                    ServiceCompanyProperties empresa = arrancado.getBean(ServiceCompanyProperties.class);
                    assertThat(empresa.getCodigoHojaDeVida()).isEqualTo("FT-001");
                    assertThat(empresa.getGarantia()).isNull();
                });
    }
}
