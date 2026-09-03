package com.malphasos.malphasos.equipment.application.services.equipment.commands;

import java.util.UUID;

/** Retira la asociación. No la borra: la deja inactiva. */
public record DeactivateEquipmentCommand(UUID id) {
}
