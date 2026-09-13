package com.malphasos.malphasos.workorder.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Añade un equipo al alcance de la orden.
 *
 * <p><b>No lleva el área, y esa ausencia es la regla.</b> El área que queda congelada en la orden la
 * averigua el servidor consultando dónde está el equipo ahora. Si el cliente del API la enviara,
 * podría enviar una donde el equipo no está y el registro histórico nacería mintiendo sobre dónde
 * se prestó el servicio.
 */
@Schema(name = "WorkOrderEquipmentRequest")
public record WorkOrderEquipmentRequest(
        @NotNull(message = "El equipo es obligatorio") UUID idEquipoCliente) {
}
