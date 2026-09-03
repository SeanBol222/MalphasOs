package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Registra que una marca fabrica un tipo de equipo. */
@Schema(name = "EquipmentCreateRequest")
public record EquipmentCreateRequest(
        @NotNull(message = "El tipo de equipo es obligatorio") UUID idTipoEquipo,
        @NotNull(message = "La marca es obligatoria") UUID idMarca) {
}
