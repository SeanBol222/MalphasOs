package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Alta de un teléfono de contacto del cliente. */
public record AddClientPhoneCommand(UUID idCliente, String telefono) {
}
