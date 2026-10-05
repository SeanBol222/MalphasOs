package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Alta de un modelo.
 *
 * <p>El <b>nombre</b> es obligatorio —«IdeaPad 3» es lo que distingue este modelo de los otros
 * portátiles de Lenovo— y el registro INVIMA es opcional: se tramita después del alta.
 */
@Schema(name = "ModelCreateRequest")
public record ModelCreateRequest(
        @NotBlank(message = "El nombre del modelo es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        @Schema(description = "Nombre comercial del modelo", example = "IdeaPad 3")
        String nombre,

        @Size(max = 50, message = "El registro INVIMA no puede pasar de 50 caracteres")
        String invima,
        @NotNull(message = "El fabricante es obligatorio") UUID idFabricante,
        @NotNull(message = "El equipo es obligatorio") UUID idEquipo,
        @Valid @Schema(description = "Opcional: la ficha se puede llenar despues")
        TechnicalSheetRequest fichaTecnica) {
}
