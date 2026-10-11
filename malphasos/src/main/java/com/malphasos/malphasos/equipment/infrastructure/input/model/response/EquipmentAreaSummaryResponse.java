package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "EquipmentAreaSummaryResponse", description = "Los equipos en servicio de un area, contados")
public record EquipmentAreaSummaryResponse(
        UUID idArea,
        UUID idSede,
        UUID idCliente,
        long equipos,
        @Schema(description = "Los que quedaron fuera de servicio en su ultimo servicio cerrado") long fueraDeServicio,
        @Schema(description = "El mas reciente de los ultimos servicios de sus equipos") LocalDateTime ultimoServicio) {
}
