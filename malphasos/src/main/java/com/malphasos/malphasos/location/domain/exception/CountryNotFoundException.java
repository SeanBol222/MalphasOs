package com.malphasos.malphasos.location.domain.exception;

import java.util.UUID;

/**
 * No existe un país con ese identificador.
 *
 * <p>Vive en {@code domain/exception} y no en {@code infrastructure/output/errors}, donde el
 * original la ubicaba: es una condición del dominio, no un fallo de la tecnología que persiste. Que
 * estuviera del otro lado obligaba a la capa de aplicación a importar desde infraestructura.
 */
public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(UUID id) {
        super("No existe un pais con el identificador " + id);
    }
}
