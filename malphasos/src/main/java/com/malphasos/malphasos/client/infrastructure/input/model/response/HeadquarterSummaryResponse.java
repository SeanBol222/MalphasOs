package com.malphasos.malphasos.client.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "HeadquarterSummaryResponse", description = "Una sede con sus areas, para los listados")
public record HeadquarterSummaryResponse(
        UUID id,
        String nombre,
        UUID idCliente,
        UUID idCiudad,
        @Schema(description = "Si la sede esta abierta") boolean abierta,
        @Schema(description = "Todas sus areas, abiertas y cerradas, por nombre") List<Area> areas) {

    @Schema(name = "HeadquarterSummaryArea")
    public record Area(UUID id, String nombre, boolean abierta) {
    }
}
