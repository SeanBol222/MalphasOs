package com.malphasos.malphasos.report.application.model;

import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;

/**
 * El informe de un reporte, listo para imprimirse (2026-10-17): el reporte, lo que imprime de los demás
 * y el logo que le toca.
 *
 * @param congelado si el documento es el que se copió al cerrar; falso en un borrador, que lo compone en
 *     vivo
 * @param logo nulo si no lleva
 * @param firmaIngeniero nula si el ingeniero no tenía firma guardada
 * @param firmaRecibe nula mientras quien recibe no firme (V39)
 * @param recibe quien recibió, ya resuelto: lo escrito en el reporte, o la ficha de la persona registrada
 *     que firmó (V55). Nulo si nadie
 * @param aprobacion la aprobación vigente de la orden (V54), o nula mientras no la haya
 */
public record ReportDocumentView(
        ServiceReport reporte,
        ReportDocument documento,
        DocumentImage logo,
        DocumentImage firmaIngeniero,
        DocumentImage firmaRecibe,
        boolean congelado,
        Handover recibe,
        OrderApproval aprobacion) {

    /** Sin aprobación: la de antes de V54. */
    public ReportDocumentView(
            ServiceReport reporte,
            ReportDocument documento,
            DocumentImage logo,
            DocumentImage firmaIngeniero,
            DocumentImage firmaRecibe,
            boolean congelado,
            Handover recibe) {
        this(reporte, documento, logo, firmaIngeniero, firmaRecibe, congelado, recibe, null);
    }

    /** Con quien recibe tal como está escrito en el reporte: lo de siempre, antes de V55. */
    public ReportDocumentView(
            ServiceReport reporte,
            ReportDocument documento,
            DocumentImage logo,
            DocumentImage firmaIngeniero,
            DocumentImage firmaRecibe,
            boolean congelado) {
        this(reporte, documento, logo, firmaIngeniero, firmaRecibe, congelado, reporte.getRecibe());
    }
}
