package com.malphasos.malphasos.bootstrap.config.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad para cuando {@code app.security.enabled} está explícitamente en
 * {@code false}: deja pasar todas las peticiones sin autenticación.
 *
 * <p>Existe porque, sin ella, apagar la seguridad no dejaría la API abierta sino protegida por la
 * cadena por defecto de Spring Security, que exige HTTP Basic con una contraseña generada en cada
 * arranque. Ese comportamiento no corresponde a ninguna intención razonable y vuelve inaccesible la
 * documentación del API. Haciendo el estado explícito, "seguridad apagada" significa exactamente
 * eso.
 *
 * <p><b>Solo para desarrollo local.</b> Al arrancar se registra una advertencia para que nadie la
 * deje activa por accidente. Nunca debe usarse en un entorno desplegado.
 *
 * @see SecurityConfig configuración real, activa por omisión
 */
@Slf4j
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
public class SecurityDisabledConfig {

    @PostConstruct
    void advertir() {
        log.warn("=================================================================");
        log.warn(" SEGURIDAD DESACTIVADA: todos los endpoints estan abiertos.");
        log.warn(" app.security.enabled=false. Solo para desarrollo local.");
        log.warn("=================================================================");
    }

    @Bean
    public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
