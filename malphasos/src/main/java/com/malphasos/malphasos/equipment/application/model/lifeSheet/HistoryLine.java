package com.malphasos.malphasos.equipment.application.model.lifeSheet;

import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Una línea del historial de la hoja de vida, venga de un reporte cerrado de MalphasOS o de lo que otra
 * empresa le hizo al equipo (V61, 2026-11-23). Las dos se ordenan juntas por fecha: una calibración externa
 * de ayer va por encima del mantenimiento de la semana pasada.
 *
 * @param momento con qué se ordenan dos del mismo día: el cierre del reporte, o el registro de la externa
 * @param resultado cómo quedó el equipo; en una externa puede no saberse, y entonces es nulo
 * @param responsable el ingeniero, en una de reporte; nulo en una externa
 * @param idReporteServicio el reporte del que sale, o nulo si es externa
 * @param idIntervencionExterna la externa de la que sale, o nulo si es de un reporte
 * @param empresaExterna quién la hizo, en una externa
 * @param documento el certificado o informe del tercero, en una externa, si se anotó
 */
public record HistoryLine(
        LocalDate fecha,
        LocalDateTime momento,
        String tipoServicio,
        InterventionResult resultado,
        String descripcion,
        String responsable,
        UUID idReporteServicio,
        UUID idIntervencionExterna,
        String empresaExterna,
        String documento) {

    /** De la más reciente a la más antigua. */
    public static final Comparator<HistoryLine> RECIENTE_PRIMERO =
            Comparator.comparing(HistoryLine::fecha).thenComparing(HistoryLine::momento,
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed();

    public static HistoryLine of(Intervention intervencion) {
        return new HistoryLine(intervencion.fechaServicio().toLocalDate(), intervencion.fechaServicio(),
                intervencion.tipoServicio().name(), intervencion.resultado(), intervencion.descripcion(),
                intervencion.responsable(), intervencion.idReporteServicio(), null, null, null);
    }

    public static HistoryLine of(ExternalIntervention externa) {
        return new HistoryLine(externa.getRealizada(), externa.getRegistrada(), externa.getTipoServicio().name(),
                externa.getResultado(), externa.getDescripcion(), null, null, externa.getId(), externa.getEmpresa(),
                externa.getDocumento());
    }

    /** Las de los reportes y las externas, juntas y de la más reciente a la más antigua. */
    public static List<HistoryLine> merge(List<Intervention> deReportes, List<ExternalIntervention> externas) {
        return Stream.concat(deReportes.stream().map(HistoryLine::of), externas.stream().map(HistoryLine::of))
                .sorted(RECIENTE_PRIMERO)
                .toList();
    }

    public boolean externa() {
        return idIntervencionExterna != null;
    }

    /** El número del reporte, como se imprime: los seis primeros de su identificador. Nulo si es externa. */
    public String numeroReporte() {
        return idReporteServicio == null
                ? null
                : idReporteServicio.toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
