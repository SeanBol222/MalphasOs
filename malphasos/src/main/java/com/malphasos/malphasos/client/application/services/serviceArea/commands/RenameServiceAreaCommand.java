package com.malphasos.malphasos.client.application.services.serviceArea.commands;

import java.util.UUID;

/** Cambio de nombre de un área. La sede no aparece: un área no se traslada. */
public record RenameServiceAreaCommand(UUID id, String nombre) {
}
