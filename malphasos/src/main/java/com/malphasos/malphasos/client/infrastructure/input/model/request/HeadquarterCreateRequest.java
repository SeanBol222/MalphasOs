package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Apertura de una sede. El cliente va en la ruta, no en el cuerpo. */
@Schema(name = "HeadquarterCreateRequest")
public record HeadquarterCreateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre,

        @NotBlank(message = "La calle es obligatoria") String calle,
        @NotBlank(message = "La carrera es obligatoria") String carrera,
        @NotBlank(message = "El numero es obligatorio") String numero,

        @Schema(description = "Ciudad donde esta la sede")
        @NotNull(message = "La ciudad es obligatoria")
        UUID idCiudad) {
}
