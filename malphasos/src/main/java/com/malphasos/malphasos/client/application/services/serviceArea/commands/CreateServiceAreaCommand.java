package com.malphasos.malphasos.client.application.services.serviceArea.commands;

import java.util.UUID;

/** Apertura de un área de servicio dentro de una sede. */
public record CreateServiceAreaCommand(String nombre, UUID idSede) {
}
