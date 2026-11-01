package com.malphasos.malphasos.workorder.domain.exception;

import java.util.UUID;

/**
 * Esa persona no supervisa al cliente de la orden, o ya no: solo aprueba un supervisor activo de ese
 * cliente (V54). Un conflicto y no datos inválidos: la persona y la orden existen.
 */
public class NotSupervisorOfClientException extends RuntimeException {

    public NotSupervisorOfClientException(UUID idPersona, UUID idOrden) {
        super("La persona " + idPersona + " no supervisa al cliente de la orden " + idOrden);
    }
}
