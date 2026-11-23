package com.malphasos.malphasos.report.application.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que imprime el resumen de una orden (2026-10-31): una fila por equipo atendido, con su informe, su
 * resultado y quién lo recibió. Todo viene ya escrito como se imprime; la plantilla no calcula nada.
 *
 * @param logo el logo de la empresa como data: URI, o nulo
 * @param servicio «Orden N.º 1DC6F5 · Preventivo anual · Ejecutada el 18 oct 2026»
 * @param cliente «Clínica S.A.S. · NIT 900… · Sede Norte, Bogotá · Ingeniero: …»
 * @param aprobacion la línea del supervisor que aprobó la orden (V54), o nula: entonces se firma a mano
 */
public record WorkOrderSummarySheet(
        UUID idOrden,
        String numero,
        String empresa,
        String logo,
        String servicio,
        String cliente,
        String ingeniero,
        Totales totales,
        List<Fila> filas,
        ApprovalLine aprobacion) {

    public WorkOrderSummarySheet {
        filas = List.copyOf(filas);
    }

    /** Sin aprobación. */
    public WorkOrderSummarySheet(
            UUID idOrden, String numero, String empresa, String logo, String servicio, String cliente,
            String ingeniero, Totales totales, List<Fila> filas) {
        this(idOrden, numero, empresa, logo, servicio, cliente, ingeniero, totales, filas, null);
    }

    public record Totales(int equipos, int operativos, int conRestricciones, int fueraDeServicio) {
    }

    /**
     * @param destacada si el resultado no es operativo: se imprime con más peso, no con otro color
     * @param marca la marca sola, y {@code modelo} el modelo solo, para una hoja de cálculo que los filtre;
     *     nulos si el documento no los trae (2026-11-22)
     * @param cerradoEl el día en que se cerró el informe, como fecha, o nulo
     */
    public record Fila(
            int n,
            String area,
            String equipo,
            String marcaModelo,
            String serie,
            String hojaDeVida,
            String informe,
            String cerrado,
            String resultado,
            String recibio,
            boolean destacada,
            String marca,
            String modelo,
            LocalDate cerradoEl) {
    }
}
