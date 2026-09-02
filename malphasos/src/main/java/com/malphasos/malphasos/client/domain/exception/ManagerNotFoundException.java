package com.malphasos.malphasos.client.domain.exception;

import java.util.UUID;

/** No hay un encargado registrado para esa persona. */
public class ManagerNotFoundException extends RuntimeException {

    public ManagerNotFoundException(UUID idPersona) {
        super("No hay un encargado registrado para la persona " + idPersona);
    }
}
