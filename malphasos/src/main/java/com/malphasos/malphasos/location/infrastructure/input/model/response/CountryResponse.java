package com.malphasos.malphasos.location.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "CountryResponse")
public record CountryResponse(UUID id, String codigoIso, String nombre, boolean estadoActivo) {
}
