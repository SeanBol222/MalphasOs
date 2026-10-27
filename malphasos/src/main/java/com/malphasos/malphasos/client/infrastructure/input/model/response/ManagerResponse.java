package com.malphasos.malphasos.client.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Un encargado, con lo que tiene a cargo ahora.
 *
 * <p>{@code idPersona} es también su identificador: un encargado es una persona con ese rol. Desde el
 * 2026-10-27 está a cargo de varias sedes y áreas (`V41`), y la respuesta lleva las dos listas en lugar
 * de un tipo con una sede o un área. Solo las activas: de lo que se le retiró ya no responde.
 */
@Builder
@Schema(name = "ManagerResponse")
public record ManagerResponse(
        UUID idPersona,
        @Schema(description = "Sedes de las que está a cargo ahora")
        List<UUID> idsSede,
        @Schema(description = "Áreas de servicio de las que está a cargo ahora")
        List<UUID> idsAreaServicio,
        @Schema(description = "Si está a cargo de algo")
        boolean estadoActivo) {
}
