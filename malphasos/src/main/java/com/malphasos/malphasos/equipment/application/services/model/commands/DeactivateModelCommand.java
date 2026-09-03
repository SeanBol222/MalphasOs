package com.malphasos.malphasos.equipment.application.services.model.commands;

import java.util.UUID;

/** Retirada de un modelo. No lo borra: lo deja inactivo. */
public record DeactivateModelCommand(UUID id) {
}
