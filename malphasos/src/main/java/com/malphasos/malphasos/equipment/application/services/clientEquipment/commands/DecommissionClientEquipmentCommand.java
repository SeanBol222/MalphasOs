package com.malphasos.malphasos.equipment.application.services.clientEquipment.commands;

import java.util.UUID;

/** Da de baja una unidad. No la borra: la deja inactiva. */
public record DecommissionClientEquipmentCommand(UUID id) {
}
