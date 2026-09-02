package com.malphasos.malphasos.client.domain.exception;

import java.util.UUID;

/** No existe un area de servicio con ese identificador. */
public class ServiceAreaNotFoundException extends RuntimeException {

    public ServiceAreaNotFoundException(UUID id) {
        super("No existe un area de servicio con el identificador " + id);
    }
}
