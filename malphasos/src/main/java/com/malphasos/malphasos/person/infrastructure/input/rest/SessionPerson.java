package com.malphasos.malphasos.person.infrastructure.input.rest;

import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * La persona de la sesión: el {@code sub} del token es su identificador, como en el filtrado por dueño.
 *
 * <p>Lo comparten las rutas {@code /persons/me}, que son de quien llama y por eso no llevan
 * identificador. Una sesión que no es de una persona registrada —el SuperUsuario, creado a mano en
 * Keycloak— responde «esa persona no existe», con su código, en vez de un 500.
 */
final class SessionPerson {

    private SessionPerson() {
    }

    static UUID of(Authentication autenticacion) {
        String sub = autenticacion instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : autenticacion == null ? null : autenticacion.getName();
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException | NullPointerException sinPersona) {
            throw new PersonNotFoundException("La sesion no corresponde a ninguna persona registrada");
        }
    }
}
