package com.malphasos.malphasos.workorder.application.ports.output;

import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Los reportes de una orden, que desde {@code V50} son también sus equipos.
 *
 * <p>Nació el 2026-10-14 para la regla de ejecutar: una orden no se da por ejecutada mientras le quede
 * un equipo con el reporte en borrador. Con {@code V50} se quitó la tabla puente, y <b>un equipo está en
 * una orden si y solo si tiene en ella un reporte activo</b>: añadir un equipo es abrir su borrador, y
 * sacarlo, retirarlo. Los reportes son de {@code report}, y ese módulo depende de este y no al revés, así
 * que <b>la pregunta la declara este módulo y la contesta aquel</b>: es la misma inversión que
 * {@code InterventionRecordingPort} entre {@code report} y {@code equipment}.
 */
public interface WorkOrderReportsPort {

    /** El reporte activo de un equipo en una orden: lo que pone ese equipo en ella. */
    record ActiveReport(UUID idReporte, UUID idEquipoCliente, String numero, boolean cerrado) {
    }

    /** Los reportes activos de la orden, uno por equipo. */
    List<ActiveReport> activeReports(UUID idOrdenTrabajo);

    /** Lo mismo para varias órdenes en una consulta, por orden. Las que no tienen ninguno no aparecen. */
    Map<UUID, List<ActiveReport>> activeReports(Collection<UUID> idsOrdenes);

    /** Las órdenes en las que está el equipo: las que le tienen un reporte activo. */
    Set<UUID> workOrdersOf(UUID idEquipoCliente);

    /**
     * Pone el equipo en la orden abriendo su borrador, con los pasos del protocolo si el servicio es
     * preventivo y con los patrones que la orden lleva para su tipo (V60), marcados {@code desdeOrden}. Si
     * ya tiene uno activo no hace nada.
     */
    void openDraft(UUID idOrdenTrabajo, ServiceType tipoServicio, UUID idEquipoCliente, Set<UUID> patrones);

    /**
     * Lleva un cambio de los patrones de la orden a los borradores de ese tipo (V60): pone los añadidos,
     * marcados {@code desdeOrden}, en las verificaciones que el catálogo les permite, y quita los quitados
     * de donde llegaron por la orden. Lo puesto a mano no se toca, y los reportes cerrados tampoco.
     */
    void applyStandards(UUID idOrdenTrabajo, UUID idTipoEquipo, Set<UUID> puestos, Set<UUID> quitados);

    /**
     * Saca el equipo de la orden retirando su borrador. Si no tiene reporte activo no hace nada; si el
     * que tiene está cerrado, no se retira: un reporte cerrado cuenta un servicio prestado, y se
     * sustituye.
     */
    void retireDraft(UUID idOrdenTrabajo, UUID idEquipoCliente);
}
