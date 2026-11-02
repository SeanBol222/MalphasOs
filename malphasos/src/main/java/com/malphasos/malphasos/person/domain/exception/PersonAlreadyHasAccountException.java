package com.malphasos.malphasos.person.domain.exception;

import java.util.UUID;

/** Esa persona ya tiene cuenta: darle acceso otra vez crearía una segunda (2026-11-02). */
public class PersonAlreadyHasAccountException extends RuntimeException {

    public PersonAlreadyHasAccountException(UUID idPersona) {
        super("La persona " + idPersona + " ya tiene cuenta en el sistema de acceso");
    }
}
