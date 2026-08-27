package com.malphasos.malphasos.bootstrap.config.keycloak;

import lombok.RequiredArgsConstructor;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente administrativo de Keycloak, autenticado con el flujo {@code client_credentials}.
 *
 * <p>Permite crear usuarios y asignar roles contra la Admin API. Es una pieza distinta del
 * resource server de {@code config.security}: aquí la aplicación actúa como cliente de Keycloak,
 * allí actúa como servidor que valida los tokens que recibe.
 *
 * <p>Se puede desactivar con {@code keycloak.admin.enabled=false} para levantar la aplicación sin
 * un Keycloak disponible, por ejemplo en pruebas de integración que no ejercitan identidad.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.admin.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakAdminConfig {

    private final KeycloakProperties keycloakProperties;

    @Bean
    public Keycloak keycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakProperties.getServerUrl())
                .realm(keycloakProperties.getRealm())
                .clientId(keycloakProperties.getClientId())
                .clientSecret(keycloakProperties.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }
}
