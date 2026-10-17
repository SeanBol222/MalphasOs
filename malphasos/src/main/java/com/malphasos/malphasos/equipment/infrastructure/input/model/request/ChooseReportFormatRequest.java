package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import com.malphasos.malphasos.equipment.domain.equipmentType.ReportFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Con qué formato se imprime el reporte de un tipo de equipo (V32). */
@Schema(name = "ChooseReportFormatRequest")
public record ChooseReportFormatRequest(
        @NotNull(message = "Hay que decir el formato")
        @Schema(description = "MP0 si el tipo no se verifica; MP1 o MP2 si se verifica")
        ReportFormat formato) {
}
