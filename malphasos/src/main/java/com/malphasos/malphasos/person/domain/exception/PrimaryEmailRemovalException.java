package com.malphasos.malphasos.person.domain.exception;

import java.util.UUID;

/** Ese correo es el de la cuenta, y no se retira sin elegir otro antes (V40). */
public class PrimaryEmailRemovalException extends RuntimeException {

    public PrimaryEmailRemovalException(UUID idCorreo) {
        super("El correo " + idCorreo + " es el de la cuenta: elija otro como principal antes de retirarlo");
    }
}
