package com.malphasos.malphasos.report.application.model;

import java.util.List;
import java.util.UUID;

/**
 * Lo que imprime el cronograma de una orden (2026-10-31): los equipos atendidos y, mes a mes durante el año
 * siguiente, cuándo les toca el próximo mantenimiento. Es la propuesta «B · Plan de doce meses» que el
 * usuario eligió ese día. Todo viene ya escrito como se imprime; la plantilla no calcula nada.
 *
 * <p>Decidido por el usuario: <b>una sola fecha por orden</b>, porque se considera que todos los equipos se
 * atienden el mismo día, y un equipo fuera de servicio no se programa.
 *
 * @param servicio «Orden N.º 1DC6F5 · Preventivo semestral · Ejecutada el 18 oct 2026», como el resumen
 * @param realizado el día del servicio, «18 oct 2026»
 * @param proximo el del próximo mantenimiento, «18 abr 2027»
 * @param periodicidad «semestral», para la nota del pie
 * @param meses los trece del plan: el del servicio y los doce siguientes
 * @param aprobacion la línea del supervisor que aprobó la orden (V54), o nula: entonces se firma a mano
 */
public record WorkOrderScheduleSheet(
        UUID idOrden,
        String numero,
        String empresa,
        String logo,
        String servicio,
        String cliente,
        String ingeniero,
        String realizado,
        String proximo,
        String periodicidad,
        List<Mes> meses,
        List<Fila> filas,
        ApprovalLine aprobacion) {

    public WorkOrderScheduleSheet {
        meses = List.copyOf(meses);
        filas = List.copyOf(filas);
    }

    /** Sin aprobación (V54): la línea del supervisor queda para firmar a mano. */
    public WorkOrderScheduleSheet(
            UUID idOrden, String numero, String empresa, String logo, String servicio, String cliente,
            String ingeniero, String realizado, String proximo, String periodicidad, List<Mes> meses,
            List<Fila> filas) {
        this(idOrden, numero, empresa, logo, servicio, cliente, ingeniero, realizado, proximo, periodicidad,
                meses, filas, null);
    }

    /** @param anio el año, solo en el primer mes y en enero; nulo en los demás */
    public record Mes(String nombre, String anio) {
    }

    /** Qué hay en un mes de una fila. */
    public enum Marca {
        NADA, REALIZADO, PROGRAMADO
    }

    /**
     * @param marcas una por mes, en el orden de {@link WorkOrderScheduleSheet#meses()}
     * @param proximo la fecha del próximo mantenimiento, o «Fuera de servicio»
     * @param fueraDeServicio se imprime con más peso, no con otro color
     */
    public record Fila(
            int n,
            String equipo,
            String marcaModelo,
            String area,
            String serie,
            String hojaDeVida,
            List<Marca> marcas,
            String proximo,
            boolean fueraDeServicio) {

        public Fila {
            marcas = List.copyOf(marcas);
        }
    }
}
