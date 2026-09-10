package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/**
 * Se intentó trasladar una unidad a un área de servicio de otro cliente.
 *
 * <p>No es un dato inválido —el área existe y está abierta— ni algo que no exista: es una regla de
 * negocio rechazando la operación, y por eso tiene tipo propio en vez de llegar como un
 * {@link IllegalArgumentException}. Una unidad se mueve libremente entre las áreas y las sedes de
 * su cliente; cruzar a otro cliente colgaría su historial de mantenimiento de quien nunca la tuvo.
 *
 * <p>Deliberadamente <b>no</b> extiende {@code IllegalArgumentException}: el manejador de este
 * módulo traduce esa familia a 400, y este caso sale como 409 con código propio.
 */
public class CrossClientRelocationException extends RuntimeException {

    public CrossClientRelocationException(
            UUID idUnidad, UUID idAreaDestino, UUID idClienteActual, UUID idClienteDestino) {

        super("La unidad " + idUnidad + " pertenece al cliente " + idClienteActual
                + " y no puede trasladarse al area " + idAreaDestino
                + ", que es del cliente " + idClienteDestino);
    }
}
