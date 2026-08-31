package com.malphasos.malphasos.client.domain.exception;

import java.util.UUID;

/** No existe un cliente con ese identificador. */
public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID id) {
        super("No existe un cliente con el identificador " + id);
    }
}
