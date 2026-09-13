package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import java.util.UUID;

/** Saca un equipo del alcance de la orden. */
public record RemoveEquipmentFromWorkOrderCommand(UUID id, UUID idEquipoCliente) {
}
