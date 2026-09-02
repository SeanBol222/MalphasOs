package com.malphasos.malphasos.client.application.services.manager.commands;

import java.util.UUID;

/** Relevo de un encargado. No lo borra: lo deja inactivo. */
public record DeactivateManagerCommand(UUID idPersona) {
}
