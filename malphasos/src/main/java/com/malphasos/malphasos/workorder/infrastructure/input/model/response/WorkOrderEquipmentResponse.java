package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Un equipo del alcance, con el área en la que estaba al seleccionarlo. */
@Schema(name = "WorkOrderEquipmentResponse")
public record WorkOrderEquipmentResponse(
        UUID idEquipoCliente,
        @Schema(description = "Area donde estaba el equipo al anadirlo, no donde esta hoy")
        UUID idAreaServicio) {
}
