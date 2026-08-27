package com.malphasos.malphasos.bootstrap.config.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extrae los roles que Keycloak publica en el claim {@code resource_access} del token y los
 * convierte en autoridades de Spring Security.
 *
 * <p>Keycloak agrupa los roles por client, de modo que solo se toman los del client que
 * representa a esta API. Los roles de otros clients del mismo realm se ignoran.
 *
 * <p>El token es una entrada externa: cualquier desviación de la estructura esperada se resuelve
 * devolviendo una lista vacía, es decir, sin autoridades. Nunca lanza excepción por un token con
 * forma inesperada.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String clientId;

    /**
     * @param clientId identificador del client de Keycloak cuyos roles se deben leer
     */
    public KeycloakRoleConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        if (!(jwt.getClaim("resource_access") instanceof Map<?, ?> resourceAccess)) {
            return List.of();
        }

        if (!(resourceAccess.get(clientId) instanceof Map<?, ?> client)) {
            return List.of();
        }

        if (!(client.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
