package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;

/**
 * Un archivo más para el ZIP de los informes de una orden, hecho del mismo resumen que su PDF
 * (2026-11-22, v1.2.1).
 *
 * <p>Existe para que lo que se añada al ZIP viva <b>fuera</b> de este módulo y se pueda quitar sin tocarlo:
 * el primero es el resumen en .xlsx del módulo {@code orderworkbook}, provisional hasta que una aplicación
 * externa lo haga. Sin ninguna implementación, el ZIP es el de siempre.
 */
public interface WorkOrderArchiveExtension {

    /** El archivo, con su nombre dentro del ZIP. */
    ReportPdf fileFor(WorkOrderSummarySheet resumen);
}
