package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Retirada de un cliente. No lo borra: lo deja inactivo. */
public record DeactivateClientCommand(UUID id) {
}
