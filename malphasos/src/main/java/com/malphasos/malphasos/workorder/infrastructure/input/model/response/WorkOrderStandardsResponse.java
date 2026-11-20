package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Los patrones que la orden lleva para un tipo de equipo de los que tiene (V60). */
@Schema(name = "WorkOrderStandardsResponse")
public record WorkOrderStandardsResponse(
        @Schema(description = "Un tipo de equipo con equipos en la orden") UUID idTipoEquipo,
        @Schema(description = "Los patrones que lleva para el, ordenados por identificador; vacia si ninguno")
        List<UUID> patrones) {
}
