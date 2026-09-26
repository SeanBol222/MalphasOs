package com.malphasos.malphasos.bootstrap.config.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Permite que el frontend, servido desde otro origen, llame al API.
 *
 * <p><b>No existió hasta el 2026-09-26</b>, y no fue un olvido: el backend se construyó entero sin
 * frontend, de modo que nadie hizo nunca una petición desde un navegador en otro puerto. El primer
 * intento falló con «CORS header ‘Access-Control-Allow-Origin’ missing» y un 401 que engañaba: el
 * 401 no era del token, era del <b>preflight</b>. Un {@code OPTIONS} no lleva cabecera de
 * autorización, así que caía en {@code anyRequest().authenticated()} y se rechazaba antes de que
 * nadie mirase el token.
 *
 * <p><b>Va aparte de las dos cadenas de seguridad a propósito.</b> CORS es un asunto del navegador
 * y no depende de si el API exige token: hace falta igual con {@code app.security.enabled} en
 * {@code true} que en {@code false}. Ponerlo dentro de {@link SecurityConfig} habría dejado el
 * frontend roto justo en el modo que se usa para desarrollar sin Keycloak.
 *
 * <p>Los orígenes se declaran por configuración y <b>nunca con comodín</b>. Es la misma decisión que
 * toma el interceptor del frontend al adjuntar el token solo hacia este API: una regla explícita
 * dice a quién se abre la puerta, y un comodín no dice nada.
 */
@Configuration
public class CorsConfig {

    /**
     * Los verbos que el API publica de verdad. {@code OPTIONS} no se declara: lo responde la propia
     * capa de CORS antes de llegar a ningún controlador.
     */
    private static final List<String> VERBOS = List.of("GET", "POST", "PATCH", "PUT", "DELETE");

    /**
     * Cabeceras que el navegador puede enviar.
     *
     * <p>Solo dos, y las dos son necesarias: {@code Authorization} lleva el token y
     * {@code Content-Type} distingue un cuerpo JSON. Cualquier otra que hiciera falta se añade
     * aquí, que es donde se ve.
     */
    private static final List<String> CABECERAS = List.of("Authorization", "Content-Type");

    /**
     * Cuánto puede el navegador guardar la respuesta al preflight, en segundos.
     *
     * <p>Sin esto, <b>cada escritura cuesta dos viajes</b> en lugar de uno, y los umbrales de dos
     * segundos de la especificación se pagan dos veces. Media hora es suficiente para una sesión de
     * trabajo y lo bastante corto para que un cambio de configuración se note el mismo día.
     */
    private static final long CACHE_DEL_PREFLIGHT = 1800L;

    private final List<String> origenesPermitidos;

    public CorsConfig(
            @Value("${app.security.cors.allowed-origins}") List<String> origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    /**
     * La política de CORS del API.
     *
     * <p><b>No se habilita {@code allowCredentials}</b>, y esa ausencia es deliberada: el API se
     * consume con un token en la cabecera, no con cookies de sesión. Es la misma razón por la que
     * {@link SecurityConfig} desactiva CSRF. Activarlo invitaría al navegador a mandar cookies que
     * aquí nadie lee, y obligaría a una política más laxa sin ganar nada.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration politica = new CorsConfiguration();
        politica.setAllowedOrigins(origenesPermitidos);
        politica.setAllowedMethods(VERBOS);
        politica.setAllowedHeaders(CABECERAS);
        politica.setMaxAge(CACHE_DEL_PREFLIGHT);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/v1/api/**", politica);

        return fuente;
    }
}
