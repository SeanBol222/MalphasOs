package com.malphasos.malphasos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levanta un PostgreSQL real en Docker para los tests.
 *
 * <p>Se usa la misma imagen que el docker-compose del entorno local, de forma que las
 * migraciones de Flyway se ejecutan contra el mismo motor que produccion: un error de
 * sintaxis especifica de PostgreSQL falla en el test y no en el despliegue.
 *
 * <p>{@link ServiceConnection} conecta el contenedor con el DataSource de Spring
 * automaticamente, sin necesidad de declarar url, usuario ni contrasena.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
