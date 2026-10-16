package com.malphasos.malphasos.report.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Qué patrón del catálogo se usó para tomar las lecturas. */
@Schema(name = "UseReferenceStandardRequest")
public record UseReferenceStandardRequest(
        @NotNull(message = "Hay que decir que patron se uso")
        @Schema(description = "Un patron vigente del catalogo de equipos patron")
        UUID idEquipoPatron) {
}
