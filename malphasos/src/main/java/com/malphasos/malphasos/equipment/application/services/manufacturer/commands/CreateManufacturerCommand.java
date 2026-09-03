package com.malphasos.malphasos.equipment.application.services.manufacturer.commands;

import java.util.UUID;

/** Alta de un fabricante. El identificador lo genera el dominio. */
public record CreateManufacturerCommand(String nombre, UUID idPais) {
}
