package com.malphasos.malphasos.equipment.application.services.intervention.commands;

import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que hace falta para anotar una intervención en la hoja de vida de un equipo.
 *
 * <p>Los tres últimos campos son el contenido de la copia congelada: quien los manda los ha leído del
 * reporte en el momento de cerrarse, y a partir de aquí no vuelven a consultarse.
 */
public record RecordInterventionCommand(
        UUID idEquipoCliente,
        UUID idReporteServicio,
        LocalDateTime fechaServicio,
        InterventionType tipoServicio,
        InterventionResult resultado,
        String descripcion,
        String responsable,
        Set<UUID> reportesSustituidos) {

    /**
     * Los reportes cerrados de la misma orden y el mismo equipo que este cierre corrige.
     *
     * <p>Los calcula quien conoce los reportes —el módulo que los guarda— y este módulo solo los
     * recibe: así {@code equipment} no tiene que leer tablas de {@code report} para saber qué se
     * corrigió. Nunca es nulo; sin correcciones es un conjunto vacío.
     */
    public RecordInterventionCommand {
        reportesSustituidos = reportesSustituidos == null ? Set.of() : Set.copyOf(reportesSustituidos);
    }

    /** Sin descripcion ni responsable, como se anotaban hasta {@code V17}. */
    public RecordInterventionCommand(
            UUID idEquipoCliente,
            UUID idReporteServicio,
            LocalDateTime fechaServicio,
            InterventionType tipoServicio,
            InterventionResult resultado,
            Set<UUID> reportesSustituidos) {
        this(idEquipoCliente, idReporteServicio, fechaServicio, tipoServicio, resultado, null, null,
                reportesSustituidos);
    }
}
