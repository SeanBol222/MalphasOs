package com.malphasos.malphasos.client.application.services.manager.commands;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import java.util.UUID;

/** Traslado de un encargado a otra sede o a otra área. */
public record ReassignManagerCommand(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
}
