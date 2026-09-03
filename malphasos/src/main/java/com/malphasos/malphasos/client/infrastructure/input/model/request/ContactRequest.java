package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un dato de contacto del cliente: sirve para correos y para teléfonos. */
@Schema(name = "ContactRequest")
public record ContactRequest(
        @Schema(example = "contacto@hospital.com")
        @NotBlank(message = "El valor es obligatorio")
        @Size(max = 50, message = "No puede pasar de 50 caracteres")
        String valor) {
}
