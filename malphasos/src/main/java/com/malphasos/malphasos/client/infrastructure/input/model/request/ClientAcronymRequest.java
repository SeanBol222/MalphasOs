package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** La sigla corregida a mano. Se admite en minusculas y se guarda en mayusculas. */
@Schema(name = "ClientAcronymRequest")
public record ClientAcronymRequest(
        @NotBlank(message = "La sigla es obligatoria")
        @Pattern(regexp = "^\\s*[A-Za-z][A-Za-z0-9]{2,5}\\s*$",
                message = "La sigla son de tres a seis letras o digitos, empezando por letra")
        @Schema(description = "Encabeza el numero de las hojas de vida", example = "CDN")
        String sigla) {
}
