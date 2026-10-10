package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta o cambio de un protocolo: los dos campos van siempre, porque los dos son obligatorios. */
@Schema(name = "ProtocolRequest")
public record ProtocolRequest(
        @Schema(example = "Limpieza y desinfección")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String nombre,
        @Schema(example = "Limpieza externa con paño humedecido y desinfección de superficies de contacto.")
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 500, message = "La descripción no puede pasar de 500 caracteres")
        String descripcion) {
}
