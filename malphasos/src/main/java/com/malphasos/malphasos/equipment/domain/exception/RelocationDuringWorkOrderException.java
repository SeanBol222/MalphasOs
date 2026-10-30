package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/**
 * Se intentó trasladar una unidad que está en una orden sin ejecutar (2026-10-28, {@code V50}).
 *
 * <p>Es la regla con la que salió B6, el área que la orden guardaba de cada equipo: mientras la orden vive,
 * el área del equipo no cambia y basta leerla de la unidad; al ejecutarla queda escrita en su documento. Si
 * de verdad lo mueven a mitad de una orden, primero se saca de ella.
 *
 * <p>Como {@link CrossClientRelocationException}, no extiende {@code IllegalArgumentException}: el área de
 * destino es válida, y lo que choca es el estado de la unidad. Sale como 409 con código propio.
 */
public class RelocationDuringWorkOrderException extends RuntimeException {

    public RelocationDuringWorkOrderException(UUID idUnidad) {
        super("La unidad " + idUnidad + " esta en una orden de trabajo sin ejecutar: no se traslada hasta que"
                + " la orden se ejecute o se saque de ella");
    }
}
