package com.malphasos.malphasos.client.domain.exception;

import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.UUID;

/**
 * Esa persona no puede estar a cargo de una sede ni de un área (V46, 2026-10-27).
 *
 * <p>Decidido por el usuario al quitar el segundo tipo de persona: pueden estarlo los encargados y los
 * representantes del cliente, y nadie más. Hasta entonces el servicio aceptaba a cualquier persona que
 * existiera, también a un ingeniero. Es un conflicto y no datos inválidos: la persona existe y la sede
 * también, pero esa persona no es de las que responden por una.
 */
public class ManagerPersonTypeException extends RuntimeException {

    public ManagerPersonTypeException(UUID idPersona, PersonType tipo) {
        super("La persona " + idPersona + " es " + tipo
                + ": solo un encargado o un representante del cliente puede estar a cargo de una sede o un area");
    }
}
