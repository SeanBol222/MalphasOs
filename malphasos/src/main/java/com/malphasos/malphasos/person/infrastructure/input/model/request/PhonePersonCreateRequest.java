package com.malphasos.malphasos.person.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** Teléfono que se agrega a una persona. */
@Builder
@Schema(name = "PhonePersonCreateRequest")
public record PhonePersonCreateRequest(
        @Schema(description = "Numero de telefono", example = "3001234567")
        @NotBlank(message = "El telefono es obligatorio")
        @Size(max = 10, message = "El telefono no puede exceder 10 caracteres")
        String telefonoPersona) {
}
