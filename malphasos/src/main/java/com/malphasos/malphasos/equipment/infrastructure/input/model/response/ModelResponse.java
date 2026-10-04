package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "ModelResponse")
public record ModelResponse(
        UUID id,
        @Schema(description = "Nombre comercial del modelo", example = "IdeaPad 3") String nombre,
        String invima,
        UUID idFabricante,
        UUID idEquipo,
        boolean estadoActivo) {
}
