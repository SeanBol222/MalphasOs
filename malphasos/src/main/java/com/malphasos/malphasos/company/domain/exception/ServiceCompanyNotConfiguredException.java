package com.malphasos.malphasos.company.domain.exception;

/** La empresa todavía no se ha configurado: ni el archivo de arranque la traía ni nadie la ha llenado. */
public class ServiceCompanyNotConfiguredException extends RuntimeException {

    public ServiceCompanyNotConfiguredException() {
        super("La empresa que presta el servicio todavia no se ha configurado");
    }
}
