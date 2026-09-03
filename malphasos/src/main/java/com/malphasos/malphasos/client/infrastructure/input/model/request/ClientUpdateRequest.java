package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cambio sobre un cliente. Un campo ausente deja el valor como está.
 *
 * <p>Ni el documento ni el tipo aparecen: identifican al cliente frente al Estado y no se cambian.
 */
@Schema(name = "ClientUpdateRequest")
public record ClientUpdateRequest(
        @Size(max = 50, message = "La razon social no puede pasar de 50 caracteres")
        String razonSocial,
        UUID idPais) {
}
