package com.malphasos.malphasos.equipment.application.services.protocol.commands;

import java.util.UUID;

/** Cambio del nombre y la descripción de un protocolo. */
public record UpdateProtocolCommand(UUID id, String nombre, String descripcion) {
}
