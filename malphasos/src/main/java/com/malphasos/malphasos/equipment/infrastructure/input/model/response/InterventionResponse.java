package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una línea del historial de intervenciones de un equipo: la cuarta sección de su hoja de vida.
 *
 * <p>Lleva los tres datos que RF-27 exige —fecha de servicio, tipo y resultado— más el reporte del
 * que salen, para que desde el historial se pueda abrir el reporte completo.
 */
@Schema(name = "InterventionResponse", description = "Una intervencion registrada en la hoja de vida")
public record InterventionResponse(
        @Schema(description = "Identificador de la intervencion") UUID id,
        @Schema(description = "Equipo instalado al que se le hizo") UUID idEquipoCliente,
        @Schema(description = "Reporte de servicio del que sale, para abrirlo desde el historial")
        UUID idReporteServicio,
        @Schema(description = "Cuando se completo el servicio", example = "2026-10-04T15:30:00")
        LocalDateTime fechaServicio,
        @Schema(description = "Tipo de servicio prestado", example = "PREVENTIVO") String tipoServicio,
        @Schema(description = "Estado en que quedo el equipo", example = "OPERATIVO") String resultado) {
}
