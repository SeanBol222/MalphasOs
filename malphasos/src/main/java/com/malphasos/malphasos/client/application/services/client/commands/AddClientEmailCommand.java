package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Alta de un correo de contacto del cliente. */
public record AddClientEmailCommand(UUID idCliente, String correo) {
}
