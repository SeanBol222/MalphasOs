package com.malphasos.malphasos.bootstrap.config.security;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configura la aplicación como resource server OAuth2: valida los JWT que emite Keycloak y traduce
 * sus roles a autoridades de Spring Security.
 *
 * <p>La API es stateless y se consume con token, por eso CSRF queda deshabilitado: la protección
 * CSRF cubre ataques basados en cookies de sesión, que aquí no existen.
 *
 * <p>{@code @EnableMethodSecurity} habilita {@code @PreAuthorize} en los controladores, de modo que
 * la autorización se declara operación por operación y no solo por ruta. Cada operación nombra
 * únicamente la autoridad de su recurso; quién es administrador lo decide {@link ApiAuthority}, no
 * las anotaciones.
 *
 * <p>Toda la configuración es condicional a {@code app.security.enabled}, que por omisión está
 * activa: si la propiedad falta, la aplicación queda protegida. Solo se apaga de forma explícita.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    /**
     * Rutas públicas: la documentación del API y el endpoint de salud.
     *
     * <p>{@code /actuator/health} debe ser accesible sin token para que el healthcheck del
     * contenedor pueda consultarlo. No expone información sensible: sin autenticación devuelve
     * únicamente el estado global, nunca el desglose por componente.
     */
    private static final String[] PUBLIC_PATHS = {
        "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/actuator/health/**"
    };

    /** Client de Keycloak cuyos roles se leen del token. */
    private final String clientId;

    public SecurityConfig(@Value("${app.security.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter(clientId));

        return converter;
    }

    /**
     * Declara ante Spring Security la misma jerarquía que {@link ApiAuthority} aplica al convertir
     * el token: {@code super.admin.full} implica {@code admin.full}, y {@code admin.full} implica
     * todas las autoridades de recurso.
     *
     * <p>Puede parecer que duplica la expansión del converter, pero cubre un caso que aquel no
     * alcanza: el converter solo interviene cuando la autenticación nace de un JWT que pasa por
     * esta cadena de filtros. Cualquier autenticación construida por otra vía llegaría a
     * {@code @PreAuthorize} con las autoridades crudas y un administrador se vería rechazado. La
     * jerarquía cierra ese hueco sin repetir el modelo: ambas se derivan de la misma constante.
     *
     * <p>Los nombres van sin el prefijo {@code ROLE_}. Esa convención de Spring Security no se usa
     * aquí: los permisos llegan de Keycloak con su propio nombre y se comprueban con
     * {@code hasAuthority}, nunca con {@code hasRole}.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {

        String jerarquia = Stream.concat(
                        Stream.of(ApiAuthority.SUPER_ADMIN_FULL + " > " + ApiAuthority.ADMIN_FULL),
                        ApiAuthority.RESOURCE_AUTHORITIES.stream()
                                .map(autoridad -> ApiAuthority.ADMIN_FULL + " > " + autoridad))
                .collect(Collectors.joining("\n"));

        return RoleHierarchyImpl.fromHierarchy(jerarquia);
    }
}
