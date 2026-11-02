package com.malphasos.malphasos.report.application.services.serviceReport.sheet;

import com.malphasos.malphasos.report.application.model.ApprovalLine;
import com.malphasos.malphasos.report.application.model.OrderApproval;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cómo se escriben números y fechas en el informe: como en Colombia, y como los escribía el navegador
 * cuando el informe se componía allí (2026-10-17).
 */
public final class SheetFormat {

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    /** Lo que dice el informe cuando un dato no está registrado. */
    public static final String SIN_DATO = "—";

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd MMM yyyy", COLOMBIA);

    private SheetFormat() {
    }

    /** «10,5», «1.234,5678»: hasta cuatro decimales, sin ceros de sobra. */
    public static String numero(BigDecimal valor) {
        // DecimalFormat no es seguro entre hilos: uno por llamada.
        DecimalFormat formato = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(COLOMBIA));

        return formato.format(valor == null ? BigDecimal.ZERO : valor);
    }

    /** «+0,5», «−0,5», «0». Con el signo menos de verdad, no el guion. */
    public static String conSigno(BigDecimal valor) {
        if (valor == null || valor.signum() == 0) {
            return "0";
        }

        return (valor.signum() > 0 ? "+" : "−") + numero(valor.abs());
    }

    /** «17 oct 2026», «05 sept 2026», o la raya si no hay fecha. Java ya abrevia sin punto en es-CO. */
    public static String fechaCorta(LocalDate fecha) {
        return fecha == null ? SIN_DATO : FECHA_CORTA.format(fecha);
    }

    /** El texto, o la raya si no hay. */
    public static String oRaya(String texto) {
        return texto == null || texto.isBlank() ? SIN_DATO : texto;
    }

    /**
     * La línea de quien aprobó la orden (V54): «Supervisor responsable · Ingeniero biomédico · Aprobó el 2
     * nov 2026», con su firma. Nula sin aprobación, y entonces cada documento deja la suya para firmar a mano.
     */
    public static ApprovalLine lineaDeAprobacion(OrderApproval aprobacion) {
        if (aprobacion == null) {
            return null;
        }
        String cargo = aprobacion.cargo() == null || aprobacion.cargo().isBlank() ? null : aprobacion.cargo();

        return new ApprovalLine(
                oRaya(aprobacion.nombre()),
                "Supervisor responsable" + (cargo == null ? "" : " · " + cargo)
                        + " · Aprobó el " + fechaCorta(aprobacion.fecha()),
                aprobacion.firma() == null ? null : aprobacion.firma().dataUri());
    }
}
