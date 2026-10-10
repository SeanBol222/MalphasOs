package com.malphasos.malphasos.report.infrastructure.input.model.request;

import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cómo quedó un paso del protocolo. Con {@code NO_APLICA} la nota es obligatoria. */
@Schema(name = "MarkReportStepRequest")
public record MarkReportStepRequest(
        @NotNull(message = "El estado del paso es obligatorio")
        StepState estado,
        @Schema(example = "El equipo no tiene batería")
        @Size(max = 300, message = "La nota no puede pasar de 300 caracteres")
        String nota) {
}
