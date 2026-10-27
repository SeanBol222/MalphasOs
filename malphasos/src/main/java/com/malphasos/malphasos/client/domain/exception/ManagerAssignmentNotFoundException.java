package com.malphasos.malphasos.client.domain.exception;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import java.util.UUID;

/** Esa persona no está a cargo de esa sede o de esa área (2026-10-27). */
public class ManagerAssignmentNotFoundException extends RuntimeException {

    public ManagerAssignmentNotFoundException(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
        super("La persona " + idPersona + " no esta a cargo de "
                + (tipo == ManagerType.HEADQUARTER ? "la sede " : "el area ") + idAsignacion);
    }
}
