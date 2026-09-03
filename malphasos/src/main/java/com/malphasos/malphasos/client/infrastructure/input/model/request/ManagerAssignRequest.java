package com.malphasos.malphasos.client.infrastructure.input.model.request;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Pone al frente de una sede o de un área a alguien que ya existe como persona. */
@Schema(name = "ManagerAssignRequest")
public record ManagerAssignRequest(
        @NotNull(message = "La persona es obligatoria") UUID idPersona,
        @NotNull(message = "El tipo de encargado es obligatorio") ManagerType tipo,
        @NotNull(message = "La asignacion es obligatoria") UUID idAsignacion) {
}
