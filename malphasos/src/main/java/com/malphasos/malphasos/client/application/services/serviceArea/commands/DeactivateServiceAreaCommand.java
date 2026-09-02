package com.malphasos.malphasos.client.application.services.serviceArea.commands;

import java.util.UUID;

/** Cierre de un área de servicio. No la borra: la deja inactiva. */
public record DeactivateServiceAreaCommand(UUID id) {
}
