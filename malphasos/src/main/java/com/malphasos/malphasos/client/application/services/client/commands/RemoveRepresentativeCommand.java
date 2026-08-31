package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Retirada de una persona de la representación legal de un cliente. */
public record RemoveRepresentativeCommand(UUID idCliente, UUID idPersona) {
}
