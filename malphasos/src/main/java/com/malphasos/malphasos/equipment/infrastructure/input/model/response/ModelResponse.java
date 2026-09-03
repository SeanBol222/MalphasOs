package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "ModelResponse")
public record ModelResponse(
        UUID id, String invima, UUID idFabricante, UUID idEquipo, boolean estadoActivo) {
}
