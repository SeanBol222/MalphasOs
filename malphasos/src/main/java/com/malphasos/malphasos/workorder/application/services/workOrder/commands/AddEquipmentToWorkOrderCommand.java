package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import java.util.UUID;

/**
 * Añade un equipo al alcance de la orden.
 *
 * <p><b>No lleva el área</b>, y esa ausencia es la regla. El área que se congela en la orden la
 * averigua el servicio consultando dónde está el equipo ahora mismo. Si el llamante la declarase,
 * podría declarar una donde el equipo no está —por error o a propósito— y el registro histórico
 * nacería mintiendo. No se puede mentir sobre lo que no se puede decir.
 */
public record AddEquipmentToWorkOrderCommand(UUID id, UUID idEquipoCliente) {
}
