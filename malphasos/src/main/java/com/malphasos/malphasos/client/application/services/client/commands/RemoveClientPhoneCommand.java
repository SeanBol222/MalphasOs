package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Retirada de un teléfono. Lo deja inactivo. */
public record RemoveClientPhoneCommand(UUID idCliente, UUID idTelefono) {
}
