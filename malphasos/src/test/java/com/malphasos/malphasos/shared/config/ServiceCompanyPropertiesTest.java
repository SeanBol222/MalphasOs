package com.malphasos.malphasos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Sin la empresa, la aplicación no arranca y dice qué copiar. Es lo que pasa al clonar el repositorio
 * sin crear config/empresa.yaml, porque el código no trae ningún valor por defecto.
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
    @DisplayName("sin nombre el contexto no arranca, y el mensaje dice qué archivo copiar")
    void sinNombre() {
        contexto.run(arrancado -> assertThat(arrancado).hasFailed()
                .getFailure().rootCause().hasMessageContaining("config/empresa.example.yaml"));
    }

    @Test
    @DisplayName("con nombre arranca, y lo demás es opcional")
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
