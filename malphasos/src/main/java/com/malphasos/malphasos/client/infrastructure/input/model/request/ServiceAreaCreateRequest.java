package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Apertura de un área de servicio. La sede va en la ruta. */
@Schema(name = "ServiceAreaCreateRequest")
public record ServiceAreaCreateRequest(
        @Schema(example = "UCI")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre) {
}
