package com.malphasos.malphasos.report.application.model;

import java.util.List;

/**
 * Los informes en PDF de todos los reportes de una orden (2026-10-31), para descargarlos juntos.
 *
 * @param nombre «informes-orden-1DC6F5.zip»
 * @param informes uno por reporte activo de la orden, todos cerrados, por su número
 */
public record ReportArchive(String nombre, List<ReportPdf> informes) {

    public ReportArchive {
        informes = List.copyOf(informes);
    }
}
