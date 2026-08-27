package com.malphasos.malphasos.bootstrap.config.openApi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI del API, servida por Swagger UI.
 *
 * <p>Además de la metadata y el esquema de seguridad Bearer/JWT, se declara un grupo por módulo de
 * negocio. Así Swagger UI presenta la documentación separada por dominio en lugar de una única
 * lista plana de endpoints. Cada módulo nuevo debe añadir aquí su grupo.
 *
 * <p>Todos los recursos siguen la convención {@code /v1/api/<recurso>}, con el recurso en plural.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    /** Prefijo común de todos los endpoints versionados del API. */
    private static final String API = "/v1/api";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MalphasOS API")
                        .version("v0.0.1")
                        .description("API REST para la gestión de mantenimientos preventivos y clientes")
                        .license(new License()
                                .name("GNU GPL v3")
                                .url("https://www.gnu.org/licenses/gpl-3.0.html")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .schemaRequirement(SECURITY_SCHEME_NAME, new SecurityScheme()
                        .name(SECURITY_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }

    /** Equipos y su perfil de mantenimiento: marcas, fabricantes, modelos, tipos y verificaciones. */
    @Bean
    public GroupedOpenApi equipmentApi() {
        return GroupedOpenApi.builder()
                .group("equipment")
                .pathsToMatch(
                        API + "/equipment/**",
                        API + "/equipment-types/**",
                        API + "/equipment-models/**",
                        API + "/brands/**",
                        API + "/manufacturers/**",
                        API + "/technical-verifications/**")
                .build();
    }

    /** Clientes y su estructura: sedes, áreas de servicio y equipos asignados. */
    @Bean
    public GroupedOpenApi clientApi() {
        return GroupedOpenApi.builder()
                .group("client")
                .pathsToMatch(
                        API + "/clients/**",
                        API + "/headquarters/**",
                        API + "/service-areas/**",
                        API + "/client-equipment/**")
                .build();
    }

    /** Personas y su identidad en Keycloak. */
    @Bean
    public GroupedOpenApi personApi() {
        return GroupedOpenApi.builder()
                .group("person")
                .pathsToMatch(API + "/persons/**")
                .build();
    }

    /** Ubicación geográfica: países y ciudades. */
    @Bean
    public GroupedOpenApi locationApi() {
        return GroupedOpenApi.builder()
                .group("location")
                .pathsToMatch(API + "/countries/**", API + "/cities/**")
                .build();
    }

    /** Reportes que agregan datos de varios módulos. */
    @Bean
    public GroupedOpenApi reportsApi() {
        return GroupedOpenApi.builder()
                .group("reports")
                .pathsToMatch(API + "/reports/**")
                .build();
    }
}
