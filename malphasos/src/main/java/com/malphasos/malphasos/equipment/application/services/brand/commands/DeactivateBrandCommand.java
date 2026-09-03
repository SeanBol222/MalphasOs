package com.malphasos.malphasos.equipment.application.services.brand.commands;

import java.util.UUID;

/** Retirada de una marca. No la borra: la deja inactiva. */
public record DeactivateBrandCommand(UUID id) {
}
