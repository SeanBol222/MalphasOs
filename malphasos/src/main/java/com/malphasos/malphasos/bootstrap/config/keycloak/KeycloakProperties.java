package com.malphasos.malphasos.bootstrap.config.keycloak;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credenciales del cliente administrativo de Keycloak, leídas del prefijo {@code keycloak.admin}.
 *
 * <p>Son las que usa {@link KeycloakAdminConfig} para hablar con la Admin API, no las que validan
 * los tokens de las peticiones entrantes: eso lo configura el resource server por separado.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakProperties {

    /** URL base del servidor de Keycloak. */
    private String serverUrl;

    /** Realm sobre el que se administran usuarios y roles. */
    private String realm;

    /** Client confidencial con permisos administrativos. */
    private String clientId;

    /** Secreto del client confidencial. Se inyecta por variable de entorno, nunca se versiona. */
    private String clientSecret;
}
