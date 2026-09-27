package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/** Una lectura de la verificación, tal como se imprimirá en el reporte. */
@Schema(name = "VerificationReadingResponse")
public record VerificationReadingResponse(
        UUID id,
        @Schema(description = "Nulo si la modalidad no declara puntos")
        UUID idPuntoVerificacion,
        int secuencia,
        BigDecimal valorPatron,
        BigDecimal valorEquipo,
        String unidad) {
}
