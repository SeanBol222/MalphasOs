package com.malphasos.malphasos.equipment.application.services.manufacturer.commands;

import java.util.UUID;

/** Retirada de un fabricante. No lo borra: lo deja inactivo. */
public record DeactivateManufacturerCommand(UUID id) {
}
