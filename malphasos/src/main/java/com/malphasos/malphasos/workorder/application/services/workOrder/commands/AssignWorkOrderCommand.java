package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import java.util.UUID;

/** Pone la orden en manos de un ingeniero. */
public record AssignWorkOrderCommand(UUID id, UUID idIngeniero) {
}
