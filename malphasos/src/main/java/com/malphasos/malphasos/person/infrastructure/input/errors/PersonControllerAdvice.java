package com.malphasos.malphasos.person.infrastructure.input.errors;

import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakInvalidDataException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.infrastructure.input.rest.EmailPersonRestAdapter;
import com.malphasos.malphasos.person.infrastructure.input.rest.PersonRestAdapter;
import com.malphasos.malphasos.person.infrastructure.input.rest.PhonePersonRestAdapter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce a respuestas HTTP las excepciones propias del módulo de personas.
 *
 * <p>Está acotado a los controladores de este módulo: los errores transversales, como los de
 * validación de la petición o los de acceso a datos, los atiende el advice de {@code bootstrap}.
 *
 * <p>No declara un manejador para {@code Exception}. El del proyecto original sí lo hacía y, además
 * de devolver el mensaje interno de la excepción al cliente, capturaba cualquier fallo antes de que
 * llegara al advice transversal, convirtiendo en un 500 genérico errores que tenían un tratamiento
 * más preciso.
 */
@Slf4j
@RestControllerAdvice(
        assignableTypes = {
            PersonRestAdapter.class,
            EmailPersonRestAdapter.class,
            PhonePersonRestAdapter.class
        })
public class PersonControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PersonNotFoundException.class)
    public PersonErrorResponse handlePersonNotFound(PersonNotFoundException ex) {
        return PersonErrorResponse.of(PersonErrorCatalog.PERSON_NOT_FOUND, List.of(ex.getMessage()));
    }

    /**
     * Reglas de negocio incumplidas, como una combinación de tipos no permitida. Es un error del
     * cliente, no del servidor: el mensaje describe la regla y resulta útil devolverlo.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public PersonErrorResponse handleInvalidData(IllegalArgumentException ex) {
        return PersonErrorResponse.of(PersonErrorCatalog.INVALID_PERSON_DATA, List.of(ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(KeycloakUserAlreadyExistsException.class)
    public PersonErrorResponse handleUserAlreadyExists() {
        return PersonErrorResponse.of(PersonErrorCatalog.KEYCLOAK_USER_ALREADY_EXISTS);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(KeycloakInvalidDataException.class)
    public PersonErrorResponse handleKeycloakInvalidData() {
        return PersonErrorResponse.of(PersonErrorCatalog.KEYCLOAK_INVALID_DATA);
    }

    /**
     * El cliente administrativo de la aplicación no tiene permisos sobre Keycloak.
     *
     * <p>Se responde 502 y no 401 como en el original: quien carece de permisos es este servicio
     * frente a Keycloak, no quien llama al API. Un 401 le indicaría al cliente que se autentique de
     * nuevo, cuando el problema es una configuración incorrecta del servidor.
     */
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(KeycloakUnauthorizedException.class)
    public PersonErrorResponse handleKeycloakUnauthorized(KeycloakUnauthorizedException ex) {
        log.error("El cliente administrativo no puede operar sobre Keycloak", ex);

        return PersonErrorResponse.of(PersonErrorCatalog.KEYCLOAK_UNAUTHORIZED);
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(KeycloakConnectionException.class)
    public PersonErrorResponse handleKeycloakConnection(KeycloakConnectionException ex) {
        log.error("Fallo de comunicacion con Keycloak", ex);

        return PersonErrorResponse.of(PersonErrorCatalog.KEYCLOAK_CONNECTION_ERROR);
    }
}
