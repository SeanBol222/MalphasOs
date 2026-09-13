package com.malphasos.malphasos.workorder.domain.exception;

import java.util.UUID;

/** No existe una orden de trabajo con ese identificador. */
public class WorkOrderNotFoundException extends RuntimeException {

    public WorkOrderNotFoundException(UUID id) {
        super("No existe la orden de trabajo " + id);
    }
}
