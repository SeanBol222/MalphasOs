package com.malphasos.malphasos.report.infrastructure.input.model.request;

import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Cómo llegó el equipo: las dos verificaciones al ingreso y sus accesorios (V31). */
@Schema(name = "RecordIntakeRequest")
public record RecordIntakeRequest(
        @NotNull(message = "Falta el estado de la estructura fisica al ingreso")
        @Schema(description = "Estado de la estructura fisica del equipo al recibirlo")
        IntakeState fisico,
        @Size(max = 500, message = "La observacion no puede pasar de 500 caracteres")
        @Schema(description = "Obligatoria si no es conforme")
        String observacionFisico,
        @NotNull(message = "Falta el estado operativo al ingreso")
        @Schema(description = "Estado operativo del equipo al recibirlo")
        IntakeState operativo,
        @Size(max = 500, message = "La observacion no puede pasar de 500 caracteres")
        @Schema(description = "Obligatoria si no es conforme")
        String observacionOperativo,
        @Size(max = 50, message = "Como mucho 50 accesorios")
        @Schema(description = "Sustituyen a los que hubiera, en este orden. Vacia si llego sin accesorios")
        List<@Valid @NotNull Accessory> accesorios) {

    @Schema(name = "IntakeAccessoryRequest")
    public record Accessory(
            @NotBlank(message = "Un accesorio necesita su nombre")
            @Size(max = 120, message = "El nombre de un accesorio no puede pasar de 120 caracteres")
            String nombre,
            boolean conforme,
            @Size(max = 500, message = "La observacion no puede pasar de 500 caracteres")
            @Schema(description = "Obligatoria si no es conforme")
            String observacion) {
    }
}
