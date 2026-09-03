package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un nombre y nada más: sirve para dar de alta o renombrar una marca. */
@Schema(name = "NamedRequest")
public record NamedRequest(
        @Schema(example = "Philips")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre) {
}
