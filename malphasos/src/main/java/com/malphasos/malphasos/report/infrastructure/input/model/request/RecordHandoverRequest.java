package com.malphasos.malphasos.report.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Quién recibió el equipo al entregarlo (V31). Los tres en blanco borran lo que hubiera. */
@Schema(name = "RecordHandoverRequest")
public record RecordHandoverRequest(
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        @Schema(description = "Obligatorio si se da la cedula o el cargo", example = "Ana Perez")
        String nombre,
        @Size(max = 20, message = "La cedula no puede pasar de 20 caracteres")
        String cedula,
        @Size(max = 80, message = "El cargo no puede pasar de 80 caracteres")
        @Schema(example = "Jefe de enfermeria")
        String cargo) {
}
