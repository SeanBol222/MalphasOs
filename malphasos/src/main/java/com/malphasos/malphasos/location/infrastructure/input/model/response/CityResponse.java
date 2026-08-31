package com.malphasos.malphasos.location.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "CityResponse")
public record CityResponse(UUID id, String nombre, UUID idPais, boolean estadoActivo) {
}
