package com.malphasos.malphasos.report.application.model;

/**
 * El informe de un reporte en PDF (RF-17), con el nombre con que se descarga.
 *
 * @param nombre «informe-1DC6F5.pdf»
 */
public record ReportPdf(String nombre, byte[] contenido) {

    public ReportPdf {
        contenido = contenido.clone();
    }

    @Override
    public byte[] contenido() {
        return contenido.clone();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ReportPdf pdf && nombre.equals(pdf.nombre)
                && java.util.Arrays.equals(contenido, pdf.contenido);
    }

    @Override
    public int hashCode() {
        return 31 * nombre.hashCode() + java.util.Arrays.hashCode(contenido);
    }

    @Override
    public String toString() {
        return "ReportPdf[" + nombre + ", " + contenido.length + " bytes]";
    }
}
