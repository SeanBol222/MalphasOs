package com.malphasos.malphasos.report.infrastructure.input.model.response;

import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Un paso del protocolo dentro del reporte, con su texto: el del protocolo hoy si el reporte es borrador, y
 * el de su documento si está cerrado (V51).
 */
@Schema(name = "ReportStepResponse")
public record ReportStepResponse(
        @Schema(description = "1 el primero: la prioridad del protocolo en el tipo. Nulo si el tipo ya no lo tiene,"
                + " y entonces va al final") Integer orden,
        @Schema(description = "Es el que se usa para marcarlo: un reporte no repite protocolo") UUID idProtocolo,
        String nombre,
        String descripcion,
        @Schema(description = "Lo propio del tipo del equipo. Nula si no hay") String indicacion,
        StepState estado,
        @Schema(description = "Obligatoria cuando el estado es NO_APLICA") String nota) {
}
