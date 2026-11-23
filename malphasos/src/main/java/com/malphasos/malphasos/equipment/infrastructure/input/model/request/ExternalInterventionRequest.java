package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Lo que se anota de una intervención externa (V61): cuatro datos, y dos opcionales. */
@Schema(name = "ExternalInterventionRequest", description = "Algo que otra empresa le hizo al equipo")
public record ExternalInterventionRequest(
        @Schema(description = "El dia en que se hizo; no puede ser futuro", example = "2026-11-20")
        @NotNull LocalDate realizada,
        @Schema(description = "PREVENTIVO, CORRECTIVO, CALIBRACION u OTRO", example = "CALIBRACION")
        @NotBlank String tipoServicio,
        @Schema(description = "Que se hizo", example = "Calibracion de presion en tres puntos")
        @NotBlank @Size(max = 500) String descripcion,
        @Schema(description = "La empresa que lo hizo", example = "Metrologia S.A.S.")
        @NotBlank @Size(max = 120) String empresa,
        @Schema(description = "Opcional: OPERATIVO, OPERATIVO_CON_RESTRICCIONES o FUERA_DE_SERVICIO")
        String resultado,
        @Schema(description = "Opcional: el numero del certificado o del informe del tercero", example = "CAL-2026-118")
        @Size(max = 80) String documento) {
}
