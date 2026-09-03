package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Alta o cambio de un fabricante.
 *
 * <p>Al dar de alta, el nombre es obligatorio; al cambiar, un campo ausente deja el valor como está.
 * La obligatoriedad en el alta la comprueba el dominio, que es quien tiene la regla.
 */
@Schema(name = "ManufacturerRequest")
public record ManufacturerRequest(
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres") String nombre,
        @Schema(description = "Pais de origen, opcional") UUID idPais) {
}
