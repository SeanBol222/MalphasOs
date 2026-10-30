package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import java.util.List;
import java.util.UUID;

/**
 * El historial de servicios de un equipo: la cuarta sección de su hoja de vida (2026-10-28, {@code V52}).
 *
 * <p>Hasta {@code V52} lo guardaba la tabla {@code intervencion}, que un oyente del cierre de cada reporte
 * rellenaba. Desde entonces se deriva de los reportes cerrados, y los reportes son de {@code report}, que
 * depende de este módulo y no al revés: <b>la pregunta la declara este módulo y la contesta aquel</b>, como
 * {@link WorkOrderAttendancePort}.
 */
public interface ServiceHistoryPort {

    /**
     * Una línea por cada par (orden, equipo), la de su último reporte cerrado —la regla de {@code V14}—, de
     * la más reciente a la más antigua.
     */
    List<Intervention> historyOf(UUID idEquipoCliente);
}
