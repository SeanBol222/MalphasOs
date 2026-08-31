package com.malphasos.malphasos.person.application.model.communication;

import java.util.UUID;
import lombok.Builder;

/** Correo de una persona, visto desde otro módulo. */
@Builder
public record EmailPersonCommunicationResponse(UUID idCorreoPersona, String correoPersona) {
}
