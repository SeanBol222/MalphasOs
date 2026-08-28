package com.malphasos.malphasos.person.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** Correo electrónico que se agrega a una persona. */
@Builder
@Schema(name = "EmailPersonCreateRequest")
public record EmailPersonCreateRequest(
        @Schema(description = "Correo electronico", example = "juan.perez@ejemplo.com")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 50, message = "El correo no puede exceder 50 caracteres")
        String correoPersona) {
}
