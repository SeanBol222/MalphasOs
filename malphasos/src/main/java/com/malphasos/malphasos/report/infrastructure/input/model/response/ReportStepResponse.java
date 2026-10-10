package com.malphasos.malphasos.report.infrastructure.input.model.response;

import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Un paso del protocolo dentro del reporte, con el texto que tenía el protocolo al abrirlo. */
@Schema(name = "ReportStepResponse")
public record ReportStepResponse(
        @Schema(description = "1 el primero. Es el que se usa para marcarlo") int orden,
        UUID idProtocolo,
        String nombre,
        String descripcion,
        @Schema(description = "Lo propio del tipo del equipo. Nula si no hay") String indicacion,
        StepState estado,
        @Schema(description = "Obligatoria cuando el estado es NO_APLICA") String nota) {
}
