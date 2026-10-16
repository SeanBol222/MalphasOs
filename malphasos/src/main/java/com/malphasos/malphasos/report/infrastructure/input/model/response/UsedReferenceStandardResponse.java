package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * El patrón contra el que se tomaron las lecturas, <b>como estaba el día que se eligió</b>: renovar su
 * certificado en el catálogo no cambia lo que este reporte dice. Desde V27.
 */
@Schema(name = "UsedReferenceStandardResponse")
public record UsedReferenceStandardResponse(
        UUID id,
        String nombre,
        @Schema(description = "Nula si el catalogo no la tenia") String marca,
        String serie,
        String certificado,
        LocalDate vencimiento) {
}
