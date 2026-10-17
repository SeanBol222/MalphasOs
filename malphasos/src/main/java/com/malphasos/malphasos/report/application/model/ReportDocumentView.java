package com.malphasos.malphasos.report.application.model;

import com.malphasos.malphasos.report.domain.serviceReport.DocumentImage;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;

/**
 * El informe de un reporte, listo para imprimirse (2026-10-17): el reporte, lo que imprime de los demás
 * y el logo que le toca.
 *
 * @param congelado si el documento es el que se copió al cerrar; falso en un borrador, que lo compone en
 *     vivo
 * @param logo nulo si no lleva
 * @param firmaIngeniero nula si el ingeniero no tenía firma guardada
 */
public record ReportDocumentView(
        ServiceReport reporte,
        ReportDocument documento,
        DocumentImage logo,
        DocumentImage firmaIngeniero,
        boolean congelado) {
}
