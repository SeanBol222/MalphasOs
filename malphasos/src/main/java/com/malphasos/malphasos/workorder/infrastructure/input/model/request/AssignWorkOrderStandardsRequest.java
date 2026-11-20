package com.malphasos.malphasos.workorder.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Los patrones que la orden lleva para un tipo de equipo: la lista entera. Vacía, los quita todos. */
@Schema(name = "AssignWorkOrderStandardsRequest")
public record AssignWorkOrderStandardsRequest(
        @NotNull(message = "La lista de patrones puede estar vacia, pero no faltar")
        List<@NotNull(message = "Cada patron necesita su identificador") UUID> patrones) {
}
