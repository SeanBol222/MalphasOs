package com.malphasos.malphasos.person.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** La firma, tal como la produce el lienzo: un PNG como data: URI. */
@Schema(name = "PersonSignatureRequest")
public record PersonSignatureRequest(
        @Schema(description = "PNG de hasta 256 KB como data: URI", example = "data:image/png;base64,iVBORw0KGgo=")
        @NotBlank(message = "La firma es obligatoria")
        String imagen) {
}
