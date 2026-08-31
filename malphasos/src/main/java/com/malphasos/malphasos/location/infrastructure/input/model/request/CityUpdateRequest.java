package com.malphasos.malphasos.location.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cambio sobre una ciudad. Un campo ausente deja el valor como está.
 *
 * <p>Los dos cambios son hechos distintos y registran eventos distintos: renombrar no afecta a
 * nadie, trasladar de país mueve la cobertura de todas las sedes que hay en la ciudad.
 */
@Schema(name = "CityUpdateRequest")
public record CityUpdateRequest(
        @Schema(description = "Nuevo nombre. Ausente lo deja como esta", example = "Bogota D.C.")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre,

        @Schema(description = "Pais al que se traslada. Ausente la deja donde esta")
        UUID idPais) {
}
