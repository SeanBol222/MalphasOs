package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Una intervención externa del historial de un equipo (V61). */
@Schema(name = "ExternalInterventionResponse", description = "Algo que otra empresa le hizo al equipo")
public record ExternalInterventionResponse(
        UUID id,
        UUID idEquipoCliente,
        @Schema(description = "El dia en que se hizo", example = "2026-11-20") LocalDate realizada,
        @Schema(example = "CALIBRACION") String tipoServicio,
        String descripcion,
        @Schema(description = "La empresa que lo hizo") String empresa,
        @Schema(description = "Como quedo el equipo; nulo si no se anoto") String resultado,
        @Schema(description = "Certificado o informe del tercero; nulo si no se anoto") String documento,
        @Schema(description = "Cuando se anoto en MalphasOS") LocalDateTime registrada) {

    public static ExternalInterventionResponse from(ExternalIntervention intervencion) {
        return new ExternalInterventionResponse(intervencion.getId(), intervencion.getIdEquipoCliente(),
                intervencion.getRealizada(), intervencion.getTipoServicio().name(), intervencion.getDescripcion(),
                intervencion.getEmpresa(),
                intervencion.getResultado() == null ? null : intervencion.getResultado().name(),
                intervencion.getDocumento(), intervencion.getRegistrada());
    }
}
