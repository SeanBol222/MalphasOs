package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Cambio sobre un cliente. Un campo nulo deja el valor como está. El documento no se cambia. */
public record UpdateClientCommand(UUID id, String razonSocial, UUID idPais) {
}
