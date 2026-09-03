package com.malphasos.malphasos.equipment.application.services.brand.commands;

import java.util.UUID;

/** Cambio de nombre de una marca. */
public record RenameBrandCommand(UUID id, String nombre) {
}
