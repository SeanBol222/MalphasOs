package com.malphasos.malphasos.report.domain.exception;

import java.util.UUID;

/** No existe un reporte de servicio con ese identificador. */
public class ServiceReportNotFoundException extends RuntimeException {

    public ServiceReportNotFoundException(UUID id) {
        super("No existe el reporte de servicio " + id);
    }
}
