package com.malphasos.malphasos.client.domain.exception;

/** La sigla que se quiere poner a un cliente ya la tiene otro. */
public class ClientAcronymTakenException extends RuntimeException {

    public ClientAcronymTakenException(String sigla) {
        super("La sigla " + sigla + " ya la tiene otro cliente");
    }
}
