package com.malphasos.malphasos.bootstrap.config.openApi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
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
 * Los patrones de cada grupo deben escribirse contra las rutas que los controladores publican de
 * verdad: uno que no case con ninguna no falla, simplemente deja el recurso fuera de la
 * documentación sin avisar.
 *
 * <p><b>Y por eso no se reservan patrones para lo que todavía no existe.</b> El 2026-09-28 se retiró
 * {@code /technical-verifications/**} del grupo de equipos, que llevaba ahí desde el principio
 * esperando una pieza que **acabó construyéndose en otro módulo**: el resultado de verificar vive con
 * el reporte de servicio desde {@code V9}, y ese patrón no habría casado nunca. Un patrón reservado es
 * indistinguible de un patrón roto, que es justo lo que este javadoc advierte dos párrafos arriba.
 */
@Configuration
public class OpenApiConfig {

    /** Inicio de sesión contra Keycloak desde la propia interfaz de Swagger. */
    private static final String OAUTH_SCHEME = "keycloak-oauth";

    /** Token pegado a mano, para clientes que ya lo obtuvieron por otra via. */
    private static final String BEARER_SCHEME = "bearer-jwt";

    /** Prefijo común de todos los endpoints versionados del API. */
    private static final String API = "/v1/api";

    /**
     * URL publica del realm. Es la que usa el navegador al pulsar "Authorize", de modo que debe ser
     * alcanzable desde fuera de Docker; no sirve el nombre interno del servicio.
     */
    private final String issuerUri;

    public OpenApiConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/malphasos-realm}")
                    String issuerUri) {
        this.issuerUri = issuerUri;
    }

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
                .addSecurityItem(new SecurityRequirement().addList(OAUTH_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                // Authorization Code con PKCE: el mismo flujo que usa el frontend. Swagger nunca
                // ve la contrasena, solo recibe el token que Keycloak le devuelve.
                .schemaRequirement(OAUTH_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description("Inicia sesion en Keycloak y usa el token en cada peticion")
                        .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                .authorizationUrl(issuerUri + "/protocol/openid-connect/auth")
                                .tokenUrl(issuerUri + "/protocol/openid-connect/token")
                                .scopes(new Scopes()
                                        .addString("openid", "Identificacion del usuario")
                                        .addString("profile", "Datos basicos del perfil")
                                        .addString("email", "Correo electronico")))))
                .schemaRequirement(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .description("Alternativa: pegar un token obtenido por otro medio")
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }

    /**
     * Equipos y su perfil de mantenimiento: marcas, fabricantes, modelos, tipos y verificaciones.
     *
     * <p>El inventario de un area de servicio cuelga de la ruta del area, de modo que aparece
     * tambien en el grupo de clientes: el mismo endpoint interesa desde los dos dominios.
     */
    @Bean
    public GroupedOpenApi equipmentApi() {
        return GroupedOpenApi.builder()
                .group("equipment")
                .pathsToMatch(
                        API + "/equipments/**",
                        API + "/equipment-types/**",
                        API + "/models/**",
                        API + "/brands/**",
                        API + "/manufacturers/**",
                        API + "/client-equipments/**",
                        API + "/service-areas/*/equipments")
                .build();
    }

    /** Clientes y su estructura: sedes, áreas de servicio, encargados y equipos instalados. */
    @Bean
    public GroupedOpenApi clientApi() {
        return GroupedOpenApi.builder()
                .group("client")
                .pathsToMatch(
                        API + "/clients/**",
                        API + "/headquarters/**",
                        API + "/service-areas/**",
                        API + "/managers/**")
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

    /** Ordenes de trabajo: el mantenimiento programado y su alcance. */
    @Bean
    public GroupedOpenApi workOrderApi() {
        return GroupedOpenApi.builder()
                .group("work-order")
                .pathsToMatch(API + "/work-orders/**")
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
