package com.malphasos.malphasos.equipment.application.services.protocol.commands;

import java.util.UUID;

/** Retirada de un protocolo. No lo borra: lo deja inactivo. */
public record DeactivateProtocolCommand(UUID id) {
}
