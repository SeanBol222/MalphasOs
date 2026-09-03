package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "ManufacturerResponse")
public record ManufacturerResponse(UUID id, String nombre, UUID idPais, boolean estadoActivo) {
}
