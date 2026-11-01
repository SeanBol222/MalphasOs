package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Retirada de una persona de la supervisión de un cliente. */
public record RemoveSupervisorCommand(UUID idCliente, UUID idPersona) {
}
