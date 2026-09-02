package com.malphasos.malphasos.client.application.services.headquarter.commands;

import java.util.UUID;

/** Cierre de una sede. No la borra: la deja inactiva. */
public record DeactivateHeadquarterCommand(UUID id) {
}
