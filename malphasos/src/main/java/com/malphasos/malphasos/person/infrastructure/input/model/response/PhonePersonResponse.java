package com.malphasos.malphasos.person.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

/** Teléfono de una persona tal como se devuelve al cliente. */
@Builder
@Schema(name = "PhonePersonResponse")
public record PhonePersonResponse(UUID idTelefonoPersona, String telefonoPersona, boolean estadoActivo) {
}
