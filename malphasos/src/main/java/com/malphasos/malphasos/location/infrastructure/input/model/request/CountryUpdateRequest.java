package com.malphasos.malphasos.location.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Cambio sobre un país. Un campo ausente deja el valor como está.
 *
 * <p>El código ISO no aparece: identifica al país frente al resto del mundo y no se cambia.
 */
@Schema(name = "CountryUpdateRequest")
public record CountryUpdateRequest(
        @Schema(description = "Nuevo nombre. Ausente lo deja como esta", example = "Republica de Colombia")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre) {
}
