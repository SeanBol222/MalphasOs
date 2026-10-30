package com.malphasos.malphasos.report.application.model;

import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que un reporte dice de sus pasos, sus verificaciones y sus patrones y no guarda en sus filas (V51,
 * 2026-10-28): el texto y la posición de cada paso, la unidad y la tolerancia de cada verificación y la
 * ficha de cada patrón.
 *
 * <p><b>Una sola fuente para cada momento</b>, decidido por el usuario con el análisis del esquema: de un
 * borrador se compone en vivo del catálogo, y de uno cerrado sale de su documento. Quien lo lee —el API y
 * el informe— no tiene que saber de cuál de los dos salió.
 *
 * @param pasos en su orden: la prioridad del protocolo en el tipo, y los que el tipo ya no tiene, al final
 */
public record ReportCatalog(
        List<ReportDocument.Paso> pasos,
        List<ReportDocument.Verificacion> verificaciones,
        List<ReportDocument.Patron> patrones) {

    public ReportCatalog {
        pasos = List.copyOf(pasos);
        verificaciones = List.copyOf(verificaciones);
        patrones = List.copyOf(patrones);
    }

    /** El de un reporte cerrado: el que se congeló con él. */
    public static ReportCatalog of(ReportDocument documento) {
        return new ReportCatalog(documento.pasos(), documento.verificaciones(), documento.patrones());
    }

    public Optional<ReportDocument.Paso> paso(UUID idProtocolo) {
        return pasos.stream().filter(paso -> paso.idProtocolo().equals(idProtocolo)).findFirst();
    }

    public Optional<ReportDocument.Verificacion> verificacion(UUID idVerificacion) {
        return verificaciones.stream().filter(v -> v.id().equals(idVerificacion)).findFirst();
    }

    public Optional<UsedReferenceStandard> patron(UUID idVerificacion, UUID idEquipoPatron) {
        return patrones.stream()
                .filter(p -> p.idVerificacion().equals(idVerificacion) && p.ficha().id().equals(idEquipoPatron))
                .map(ReportDocument.Patron::ficha)
                .findFirst();
    }
}
