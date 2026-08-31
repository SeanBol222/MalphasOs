package com.malphasos.malphasos.location.application.services.country.commands;

import java.util.UUID;

/** Petición de retirada de un país. No lo borra: lo deja inactivo. */
public record DeactivateCountryCommand(UUID id) {
}
