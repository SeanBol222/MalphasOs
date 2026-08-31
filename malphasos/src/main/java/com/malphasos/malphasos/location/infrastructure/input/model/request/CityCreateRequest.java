package com.malphasos.malphasos.location.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Alta de una ciudad. El identificador lo genera el dominio. */
@Schema(name = "CityCreateRequest")
public record CityCreateRequest(
        @Schema(description = "Nombre de la ciudad", example = "Bogota")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre,

        @Schema(description = "Pais al que pertenece")
        @NotNull(message = "Una ciudad pertenece siempre a un pais")
        UUID idPais) {
}
