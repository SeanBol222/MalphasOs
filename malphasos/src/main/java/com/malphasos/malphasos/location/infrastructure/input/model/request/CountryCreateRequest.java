package com.malphasos.malphasos.location.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un país.
 *
 * <p>No lleva identificador: lo genera el dominio. El DTO del original sí lo pedía, de modo que
 * quien llamara al API elegía la llave primaria del registro.
 */
@Schema(name = "CountryCreateRequest")
public record CountryCreateRequest(
        @Schema(description = "Codigo ISO 3166-1 alfa-3", example = "COL")
        @NotBlank(message = "El codigo ISO es obligatorio")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "El codigo ISO son tres letras")
        String codigoIso,

        @Schema(description = "Nombre del pais", example = "Colombia")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre) {
}
