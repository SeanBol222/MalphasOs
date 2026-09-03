package com.malphasos.malphasos.equipment.application.services.manufacturer.commands;

import java.util.UUID;

/** Cambio sobre un fabricante. Un campo nulo deja el valor como está. */
public record UpdateManufacturerCommand(UUID id, String nombre, UUID idPais) {
}
