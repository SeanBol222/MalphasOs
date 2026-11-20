package com.malphasos.malphasos.report.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un patrón contra el que se tomaron las lecturas de una verificación, <b>como estaba el día que se
 * eligió</b>: renovar su certificado en el catálogo no cambia lo que este reporte dice. Desde V27; por
 * verificación y con la ficha desde V30.
 */
@Schema(name = "UsedReferenceStandardResponse")
public record UsedReferenceStandardResponse(
        @Schema(description = "La verificacion del tipo de equipo en la que se uso") UUID idVerificacion,
        @Schema(description = "El patron del catalogo") UUID id,
        String nombre,
        @Schema(description = "Nula si el catalogo no la tenia") String marca,
        String serie,
        String certificado,
        LocalDate vencimiento,
        @Schema(description = "Nulo si el catalogo no lo tenia") String modelo,
        @Schema(description = "Nulo si el catalogo no lo tenia") String codigoInterno,
        @Schema(description = "Nulo si el catalogo no lo tenia") String rango,
        @Schema(description = "Nula si el catalogo no la tenia") String resolucion,
        @Schema(description = "Nula si el catalogo no la tenia") LocalDate fechaCalibracion,
        @Schema(description = "Nula si el catalogo no la tenia") String empresaCalibradora,
        @Schema(description = "Si lo puso la orden, que reparte sus patrones por tipo de equipo; falso si se"
                + " puso a mano") boolean desdeOrden) {
}
