package com.malphasos.malphasos.equipment.domain.exception;

/**
 * Ya hay un protocolo vigente con ese nombre, sin contar mayúsculas ni tildes.
 *
 * <p>No es un dato inválido —el nombre está bien escrito— ni algo que no exista: es que choca con otro.
 * Pedido expresamente el 2026-10-10, para que no sea fácil repetir protocolos.
 */
public class ProtocolNameTakenException extends RuntimeException {

    public ProtocolNameTakenException(String nombre, String existente) {
        super("Ya existe el protocolo «" + existente + "», que es el mismo nombre que «" + nombre
                + "» sin contar mayúsculas ni tildes");
    }
}
