package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una lectura de la verificación, tal como se imprimirá en el reporte.
 *
 * <p>Trae la verificación a la que pertenece: es lo que permite agrupar la tabla por magnitud, y lo
 * único que distingue dos lecturas sin punto de magnitudes distintas.
 *
 * <p>La unidad es la que se congeló al tomar la lectura, no la que el tipo declara hoy: reconfigurar un
 * tipo no debe cambiar un reporte ya firmado.
 */
@Schema(name = "VerificationReadingResponse")
public record VerificationReadingResponse(
        UUID id,
        UUID idVerificacion,
        @Schema(description = "Nulo si esa verificacion no declara puntos")
        UUID idPuntoVerificacion,
        int secuencia,
        BigDecimal valorPatron,
        BigDecimal valorEquipo,
        String unidad) {
}
