package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

/** La asociación entre una marca y un tipo de equipo. */
@Builder
@Schema(name = "EquipmentResponse")
public record EquipmentResponse(UUID id, UUID idTipoEquipo, UUID idMarca, boolean estadoActivo) {
}
