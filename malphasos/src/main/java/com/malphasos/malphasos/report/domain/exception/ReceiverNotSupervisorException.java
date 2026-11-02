package com.malphasos.malphasos.report.domain.exception;

import java.util.UUID;

/**
 * Quien recibe como persona registrada tiene que ser un supervisor activo del cliente de la orden (V55):
 * es el caso del supervisor que recibe y aprueba a la vez. A cualquier otra persona se la escribe por su
 * nombre, como siempre. Un conflicto y no datos inválidos: la persona y el reporte existen.
 */
public class ReceiverNotSupervisorException extends RuntimeException {

    public ReceiverNotSupervisorException(UUID idPersona) {
        super("La persona " + idPersona + " no supervisa al cliente de esta orden: escriba su nombre en vez de"
                + " elegirla");
    }
}
