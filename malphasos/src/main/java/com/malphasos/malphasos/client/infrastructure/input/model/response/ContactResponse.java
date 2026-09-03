package com.malphasos.malphasos.client.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

/** Un contacto del cliente, correo o teléfono. */
@Builder
@Schema(name = "ContactResponse")
public record ContactResponse(UUID id, String valor, boolean estadoActivo) {
}
