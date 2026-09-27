package com.malphasos.malphasos.report.domain.serviceReport.events;

import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Datos de un reporte que viajan con sus eventos.
 *
 * <p>No lleva los cinco campos de texto ni las lecturas. Quien escucha que un reporte se cerró
 * necesita saber <b>de qué equipo</b>, <b>cómo quedó</b> y <b>cuándo</b> —eso es lo que la hoja de
 * vida anota y lo que las alertas miran—; el cuerpo del reporte se consulta si hace falta. Un
 * diagnóstico de quinientos caracteres dentro de cada evento sería carga que casi nadie usa.
 */
public record ServiceReportPayload(
        UUID idOrdenTrabajo,
        UUID idEquipoCliente,
        ReportState estado,
        ServiceResult resultado,
        LocalDateTime finalizado)
        implements Payload {
}
