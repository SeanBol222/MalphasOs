package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;

/** Convierte el resumen de una orden en PDF (2026-10-31), como {@link ReportPdfPort} con el informe. */
public interface WorkOrderSummaryPdfPort {

    /** @throws ReportPdfUnavailableException si el conversor no responde o falla */
    byte[] render(WorkOrderSummarySheet hoja);
}
