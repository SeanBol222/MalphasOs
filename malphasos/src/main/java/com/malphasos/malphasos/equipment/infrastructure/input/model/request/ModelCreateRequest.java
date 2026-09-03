package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Alta de un modelo. El registro INVIMA es opcional: se tramita después. */
@Schema(name = "ModelCreateRequest")
public record ModelCreateRequest(
        @Size(max = 50, message = "El registro INVIMA no puede pasar de 50 caracteres")
        String invima,
        @NotNull(message = "El fabricante es obligatorio") UUID idFabricante,
        @NotNull(message = "El equipo es obligatorio") UUID idEquipo) {
}
