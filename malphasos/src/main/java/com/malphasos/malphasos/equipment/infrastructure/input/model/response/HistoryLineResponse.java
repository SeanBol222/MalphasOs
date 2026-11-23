package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.HistoryLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una línea del historial de la hoja de vida (V61, 2026-11-23): de un reporte cerrado o de lo que otra empresa
 * le hizo al equipo. Las de un reporte traen su número y el ingeniero; las externas, la empresa.
 */
@Schema(name = "HistoryLineResponse", description = "Una linea del historial de la hoja de vida")
public record HistoryLineResponse(
        @Schema(description = "El dia en que se hizo", example = "2026-11-20") LocalDate fecha,
        @Schema(description = "REPORTE o EXTERNA", example = "REPORTE") String origen,
        @Schema(example = "CALIBRACION") String tipoServicio,
        @Schema(description = "Como quedo el equipo; en una externa puede ser nulo") String resultado,
        String descripcion,
        @Schema(description = "El ingeniero, en una de reporte") String responsable,
        @Schema(description = "El reporte del que sale, en una de reporte") UUID idReporteServicio,
        @Schema(description = "El numero del reporte, en una de reporte", example = "5C9C83") String numeroReporte,
        @Schema(description = "La intervencion externa, en una externa") UUID idIntervencionExterna,
        @Schema(description = "Quien la hizo, en una externa") String empresaExterna,
        @Schema(description = "Certificado o informe del tercero, en una externa") String documento) {

    public static HistoryLineResponse from(HistoryLine linea) {
        return new HistoryLineResponse(linea.fecha(), linea.externa() ? "EXTERNA" : "REPORTE", linea.tipoServicio(),
                linea.resultado() == null ? null : linea.resultado().name(), linea.descripcion(), linea.responsable(),
                linea.idReporteServicio(), linea.numeroReporte(), linea.idIntervencionExterna(),
                linea.empresaExterna(), linea.documento());
    }
}
