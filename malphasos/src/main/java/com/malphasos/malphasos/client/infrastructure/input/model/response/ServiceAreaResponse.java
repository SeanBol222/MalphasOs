package com.malphasos.malphasos.client.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "ServiceAreaResponse")
public record ServiceAreaResponse(UUID id, String nombre, UUID idSede, boolean estadoActivo) {
}
