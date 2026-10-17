package com.malphasos.malphasos.report;

import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import java.time.LocalDate;
import java.util.List;

/**
 * Un documento de informe cualquiera, para las pruebas que cierran un reporte sin que lo congelado sea lo
 * que prueban (2026-10-17): cerrar lo exige.
 */
public final class UnDocumento {

    public static final ReportDocument CUALQUIERA = new ReportDocument(
            ReportDocument.VERSION,
            new ReportDocument.Orden("4F2A9C", "PREVENTIVO", false, "TRIMESTRAL", LocalDate.of(2026, 10, 1)),
            new ReportDocument.Cliente("Clínica de Ejemplo", "900.111.222-3", "Sede Norte", "Calle 1", "Ciudad",
                    "Urgencias", List.of(), List.of(), List.of()),
            new ReportDocument.Equipo("Báscula", "Kern", "MS-200", "SN-1", "HV-CDE-0001", null, null, null, null,
                    null, null, null, null, null, null),
            List.of(),
            null,
            null);

    private UnDocumento() {
    }
}
