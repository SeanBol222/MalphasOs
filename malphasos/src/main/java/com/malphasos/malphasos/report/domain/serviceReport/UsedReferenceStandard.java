package com.malphasos.malphasos.report.domain.serviceReport;

import java.time.LocalDate;
import java.util.UUID;

/**
 * El patrón contra el que se tomaron las lecturas de un reporte, <b>tal como estaba ese día</b>.
 *
 * <p>Es una copia y no una referencia viva, desde V27 (2026-10-14): renovar el certificado de un patrón
 * en el catálogo cambia su fila, y un reporte ya firmado tiene que seguir diciendo contra qué
 * certificado se midió. Guarda también el identificador, para poder llegar al patrón de hoy.
 *
 * @param marca la única opcional, como en el catálogo
 */
public record UsedReferenceStandard(
        UUID id,
        String nombre,
        String marca,
        String serie,
        String certificado,
        LocalDate vencimiento) {

    public UsedReferenceStandard {
        if (id == null || nombre == null || serie == null || certificado == null || vencimiento == null) {
            throw new IllegalArgumentException(
                    "Un patron usado necesita su identificador, nombre, serie, certificado y vencimiento:"
                            + " es lo que el reporte imprime");
        }
    }

    /** Si su certificado valía ese día. El último día cuenta: vence al terminar, no al empezar. */
    public boolean vigenteEl(LocalDate dia) {
        return !dia.isAfter(vencimiento);
    }
}
