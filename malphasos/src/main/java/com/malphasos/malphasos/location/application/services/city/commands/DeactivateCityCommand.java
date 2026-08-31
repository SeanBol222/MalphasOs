package com.malphasos.malphasos.location.application.services.city.commands;

import java.util.UUID;

/** Petición de retirada de una ciudad. No la borra: la deja inactiva. */
public record DeactivateCityCommand(UUID id) {
}
