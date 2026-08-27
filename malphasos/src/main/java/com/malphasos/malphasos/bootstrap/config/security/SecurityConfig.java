package com.malphasos.malphasos.bootstrap.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * la autorización se declara operación por operación y no solo por ruta.
 *
 * <p>Toda la configuración es condicional a {@code app.security.enabled}, que por omisión está
 * activa: si la propiedad falta, la aplicación queda protegida. Solo se apaga de forma explícita.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    /** Rutas públicas: solo la documentación del API. */
    private static final String[] PUBLIC_PATHS = {"/swagger-ui/**", "/v3/api-docs/**"};

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
}
