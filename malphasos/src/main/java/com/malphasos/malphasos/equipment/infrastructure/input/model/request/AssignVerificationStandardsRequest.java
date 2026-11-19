package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Los equipos patrón con que se hace una verificación: la lista entera. Vacía, los quita todos. */
public record AssignVerificationStandardsRequest(
        @NotNull(message = "La lista de patrones es obligatoria, aunque esté vacía")
        List<@NotNull(message = "Cada patrón necesita su identificador") UUID> patrones) {
}
