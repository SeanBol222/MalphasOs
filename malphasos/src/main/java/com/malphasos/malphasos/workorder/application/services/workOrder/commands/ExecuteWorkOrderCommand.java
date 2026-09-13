package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import java.util.UUID;

/** Da el trabajo por terminado. Desde aqui la orden ya no cambia. */
public record ExecuteWorkOrderCommand(UUID id) {
}
