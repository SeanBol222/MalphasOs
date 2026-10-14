package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "ProtocolsOfTypeResponse", description = "Los protocolos de un tipo de equipo, en orden de prioridad")
public record ProtocolsOfTypeResponse(UUID idTipoEquipo, List<AssignedProtocolResponse> protocolos) {
}
