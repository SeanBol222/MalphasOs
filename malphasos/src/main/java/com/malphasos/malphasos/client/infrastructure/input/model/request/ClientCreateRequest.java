package com.malphasos.malphasos.client.infrastructure.input.model.request;

import com.malphasos.malphasos.client.domain.client.IdentificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Alta de un cliente. El identificador lo genera el dominio. */
@Schema(name = "ClientCreateRequest")
public record ClientCreateRequest(
        @Schema(description = "NIT o documento", example = "900123456")
        @NotBlank(message = "El documento es obligatorio")
        @Size(max = 11, message = "El documento no puede pasar de 11 caracteres")
        String documento,

        @Schema(description = "Tipo de documento", example = "NIT_JURIDICO")
        @NotNull(message = "El tipo de identificacion es obligatorio")
        IdentificationType tipoIdentificacion,

        @Schema(description = "Razon social", example = "Hospital Central")
        @NotBlank(message = "La razon social es obligatoria")
        @Size(max = 50, message = "La razon social no puede pasar de 50 caracteres")
        String razonSocial,

        @Schema(description = "Pais de origen, opcional")
        UUID idPais) {
}
