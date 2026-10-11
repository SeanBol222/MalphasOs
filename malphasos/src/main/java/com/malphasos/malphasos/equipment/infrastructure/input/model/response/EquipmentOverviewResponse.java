package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "EquipmentOverviewResponse", description = "Una pagina del listado de equipos instalados")
public record EquipmentOverviewResponse(
        List<Fila> filas,
        @Schema(description = "Cuantos equipos cumplen los filtros, no cuantos trae esta pagina") long total,
        @Schema(description = "La pagina, empezando en cero") int pagina,
        int tamano) {

    @Schema(name = "EquipmentOverviewRow")
    public record Fila(
            UUID id,
            String numeroHojaVida,
            String serie,
            UUID idTipoEquipo,
            String tipoEquipo,
            String marca,
            String modelo,
            boolean estadoActivo,
            UUID idAreaServicio,
            String areaServicio,
            UUID idSede,
            String sede,
            UUID idCliente,
            String cliente,
            @Schema(description = "Fecha del ultimo servicio cerrado; nula si nunca tuvo uno") LocalDateTime ultimoServicio,
            @Schema(description = "Como quedo el equipo en ese servicio") InterventionResult resultado) {
    }
}
