package com.malphasos.malphasos.workorder.application.services.approval.commands;

import java.util.UUID;

/** Aprobación del propio supervisor, con su firma guardada. Quién es lo dice su token, no el cuerpo. */
public record ApproveFromAccountCommand(UUID idOrden, UUID idSupervisor) {
}
