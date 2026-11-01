package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** El cargo con que se nombra a un supervisor. */
@Schema(name = "ClientSupervisorRequest")
public record SupervisorRequest(
        @Schema(description = "Como se le nombra en los documentos; vacio o nulo, sin cargo",
                example = "Ingeniero biomedico")
        @Size(max = 80)
        String cargo) {
}
