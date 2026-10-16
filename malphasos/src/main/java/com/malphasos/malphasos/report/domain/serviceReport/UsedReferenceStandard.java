package com.malphasos.malphasos.report.domain.serviceReport;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un patrón contra el que se tomaron las lecturas de un reporte, <b>tal como estaba ese día</b>.
 *
 * <p>Es una copia y no una referencia viva, desde V27 (2026-10-14): renovar el certificado de un patrón
 * en el catálogo cambia su fila, y un reporte ya firmado tiene que seguir diciendo contra qué
 * certificado se midió. Guarda también el identificador, para poder llegar al patrón de hoy.
 *
 * <p>Desde V30 (2026-10-16) copia también la ficha de V29 —modelo, código interno, rango, resolución,
 * fecha de calibración y empresa calibradora—, porque es lo que el informe imprime en su tabla de
 * trazabilidad. Toda opcional, como en el catálogo.
 *
 * @param marca opcional, como en el catálogo
 */
public record UsedReferenceStandard(
        UUID id,
        String nombre,
        String marca,
        String serie,
        String certificado,
        LocalDate vencimiento,
        String modelo,
        String codigoInterno,
        String rango,
        String resolucion,
        LocalDate fechaCalibracion,
        String empresaCalibradora) {

    public UsedReferenceStandard {
        if (id == null || nombre == null || serie == null || certificado == null || vencimiento == null) {
            throw new IllegalArgumentException(
                    "Un patron usado necesita su identificador, nombre, serie, certificado y vencimiento:"
                            + " es lo que el reporte imprime");
        }
    }

    /** Sin ficha: la de los patrones copiados antes de V30, y la de uno del que no se sabe más. */
    public UsedReferenceStandard(
            UUID id, String nombre, String marca, String serie, String certificado, LocalDate vencimiento) {
        this(id, nombre, marca, serie, certificado, vencimiento, null, null, null, null, null, null);
    }

    /** Si su certificado valía ese día. El último día cuenta: vence al terminar, no al empezar. */
    public boolean vigenteEl(LocalDate dia) {
        return !dia.isAfter(vencimiento);
    }
}
