package com.malphasos.malphasos.equipment.application.services.equipment.commands;

import java.util.UUID;

/** Registra que una marca fabrica un tipo de equipo. */
public record CreateEquipmentCommand(UUID idTipoEquipo, UUID idMarca) {
}
