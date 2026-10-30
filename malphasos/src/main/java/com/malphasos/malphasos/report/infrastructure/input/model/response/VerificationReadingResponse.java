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
 * <p>La unidad y la tolerancia, y con ella la conformidad, son las de su verificación: del documento si el
 * reporte está cerrado, de modo que reconfigurar un tipo no cambia un reporte ya firmado, y del tipo hoy si
 * es borrador (V51). Hasta V51 se copiaban en cada lectura al tomarla.
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
        String unidad,
        @Schema(description = "La tolerancia de la verificacion cuando se tomo la lectura. Nula si no tenia")
        BigDecimal tolerancia,
        @Schema(description = "Equipo menos patron, con su signo") BigDecimal error,
        @Schema(description = "Si |error| <= tolerancia. Nulo sin tolerancia: no se sabe")
        Boolean conforme) {
}
