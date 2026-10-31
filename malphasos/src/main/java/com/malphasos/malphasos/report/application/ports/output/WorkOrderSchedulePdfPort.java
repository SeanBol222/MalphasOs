package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;

/** Convierte el cronograma de una orden en PDF (2026-10-31), como {@link WorkOrderSummaryPdfPort}. */
public interface WorkOrderSchedulePdfPort {

    /** @throws ReportPdfUnavailableException si el conversor no responde o falla */
    byte[] render(WorkOrderScheduleSheet hoja);
}
