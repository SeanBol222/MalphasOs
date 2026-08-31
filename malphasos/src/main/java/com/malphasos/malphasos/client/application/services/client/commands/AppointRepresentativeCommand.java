package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Nombramiento de una persona como representante legal de un cliente. */
public record AppointRepresentativeCommand(UUID idCliente, UUID idPersona) {
}
