package com.malphasos.malphasos.equipment.application.services.clientEquipment.commands;

import java.util.UUID;

/** Traslada una unidad a otra área de servicio. */
public record RelocateClientEquipmentCommand(UUID id, UUID idAreaServicio) {
}
