package com.malphasos.malphasos.client.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "HeadquarterResponse")
public record HeadquarterResponse(
        UUID id,
        String nombre,
        String calle,
        String carrera,
        String numero,
        UUID idCliente,
        UUID idCiudad,
        boolean estadoActivo) {
}
