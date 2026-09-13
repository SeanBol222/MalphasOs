package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import java.util.UUID;

/** Retira la orden sin borrarla. No es lo mismo que ejecutarla. */
public record CancelWorkOrderCommand(UUID id) {
}
