package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Retirada de un correo. Lo deja inactivo. */
public record RemoveClientEmailCommand(UUID idCliente, UUID idCorreo) {
}
