package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import java.util.UUID;

/** Retirada de un tipo de equipo. No lo borra: lo deja inactivo. */
public record DeactivateEquipmentTypeCommand(UUID id) {
}
