package com.malphasos.malphasos.person.application.model.communication;

import java.util.UUID;
import lombok.Builder;

/**
 * Teléfono de una persona, visto desde otro módulo.
 *
 * <p>En el original esta clase estaba <b>vacía</b>, sin un solo campo, de modo que la lista de
 * teléfonos llegaba al módulo que la pedía como una colección de objetos sin contenido.
 */
@Builder
public record PhonePersonCommunicationResponse(UUID idTelefonoPersona, String telefonoPersona) {
}
