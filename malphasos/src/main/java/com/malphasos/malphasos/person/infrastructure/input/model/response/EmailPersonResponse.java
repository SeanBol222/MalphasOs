package com.malphasos.malphasos.person.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

/** Correo de una persona tal como se devuelve al cliente. */
@Builder
@Schema(name = "EmailPersonResponse")
public record EmailPersonResponse(
        UUID idCorreoPersona,
        String correoPersona,
        boolean estadoActivo,
        @Schema(description = "Si es el correo de la cuenta en Keycloak (V40)") boolean principal) {
}
