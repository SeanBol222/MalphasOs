package com.malphasos.malphasos.client.domain.exception;

import java.util.UUID;

/**
 * Esa persona ya está a cargo de algo de otro cliente (2026-10-27).
 *
 * <p>Un encargado es personal de un cliente: todas sus asignaciones activas son del mismo, decidido
 * por el usuario. El esquema no puede comprobarlo —la sede y el área viven en tablas distintas—, así
 * que es una regla del servicio, y un conflicto y no datos inválidos: la sede existe y está bien
 * escrita, pero es de otro cliente.
 */
public class ManagerOfAnotherClientException extends RuntimeException {

    public ManagerOfAnotherClientException(UUID idPersona, UUID idClienteActual, UUID idClienteNuevo) {
        super("La persona " + idPersona + " ya esta a cargo de sedes o areas del cliente "
                + idClienteActual + " y no puede estarlo tambien del cliente " + idClienteNuevo);
    }
}
