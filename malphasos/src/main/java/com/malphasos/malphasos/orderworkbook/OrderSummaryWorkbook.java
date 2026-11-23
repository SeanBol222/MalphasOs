package com.malphasos.malphasos.orderworkbook;

import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.ports.output.WorkOrderArchiveExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

/**
 * El resumen de una orden como hoja de cálculo .xlsx (Office Open XML, ISO/IEC 29500: lo abren Excel,
 * LibreOffice y Google Sheets). <b>Provisional</b>: ver {@code package-info}.
 *
 * <p>Arriba, la orden, el cliente, el ingeniero y los totales; debajo, una fila por equipo, con la
 * cabecera fija y filtros. Lo que el PDF imprime con raya, aquí va vacío: una celda con «—» no se filtra
 * ni se cuenta bien.
 */
@Component
public class OrderSummaryWorkbook implements WorkOrderArchiveExtension {

    static final List<String> COLUMNAS = List.of(
            "#", "Área", "Equipo", "Marca", "Modelo", "Serie", "Hoja de vida", "Informe", "Cerrado",
            "Resultado", "Recibió");
    private static final double[] ANCHOS = {5, 22, 28, 18, 22, 18, 16, 11, 12, 26, 28};
    private static final String SIN_DATO = "—";
    /** La fila de la cabecera de la tabla, contando desde cero. */
    static final int CABECERA = 6;

    @Override
    public ReportPdf fileFor(WorkOrderSummarySheet resumen) {
        return new ReportPdf("orden-" + resumen.numero() + ".xlsx", escribir(resumen));
    }

    private static byte[] escribir(WorkOrderSummarySheet resumen) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Workbook libro = new Workbook(salida, "MalphasOS", "1.0");
            Worksheet hoja = libro.newWorksheet("Orden " + resumen.numero());

            encabezado(hoja, resumen);
            tabla(hoja, resumen.filas());

            libro.finish();
        } catch (IOException e) {
            // En memoria no hay disco que falle: si pasa, es un defecto, no una condición del usuario.
            throw new UncheckedIOException("No se pudo escribir el resumen de la orden en .xlsx", e);
        }
        return salida.toByteArray();
    }

    private static void encabezado(Worksheet hoja, WorkOrderSummarySheet resumen) {
        WorkOrderSummarySheet.Totales totales = resumen.totales();

        hoja.value(0, 0, "Resumen de la orden " + resumen.numero());
        hoja.style(0, 0).bold().fontSize(14).set();
        hoja.value(1, 0, resumen.servicio());
        hoja.value(2, 0, resumen.cliente());
        hoja.value(3, 0, "Ingeniero: " + resumen.ingeniero());
        hoja.value(4, 0, "Equipos: " + totales.equipos()
                + " · Operativos: " + totales.operativos()
                + " · Con restricciones: " + totales.conRestricciones()
                + " · Fuera de servicio: " + totales.fueraDeServicio());
    }

    private static void tabla(Worksheet hoja, List<WorkOrderSummarySheet.Fila> filas) {
        for (int columna = 0; columna < COLUMNAS.size(); columna++) {
            hoja.value(CABECERA, columna, COLUMNAS.get(columna));
            hoja.style(CABECERA, columna).bold().fillColor("E8E4E1").set();
            hoja.width(columna, ANCHOS[columna]);
        }

        int fila = CABECERA;
        for (WorkOrderSummarySheet.Fila equipo : filas) {
            fila++;
            hoja.value(fila, 0, equipo.n());
            texto(hoja, fila, 1, equipo.area());
            texto(hoja, fila, 2, equipo.equipo());
            texto(hoja, fila, 3, equipo.marca());
            texto(hoja, fila, 4, equipo.modelo());
            texto(hoja, fila, 5, equipo.serie());
            texto(hoja, fila, 6, equipo.hojaDeVida());
            texto(hoja, fila, 7, equipo.informe());
            if (equipo.cerradoEl() != null) {
                hoja.value(fila, 8, equipo.cerradoEl());
                hoja.style(fila, 8).format("dd/mm/yyyy").set();
            }
            texto(hoja, fila, 9, equipo.resultado());
            texto(hoja, fila, 10, equipo.recibio());
            if (equipo.destacada()) {
                // Como el PDF: lo que no quedó operativo pesa más, sin colores nuevos.
                hoja.style(fila, 9).bold().set();
            }
        }

        hoja.freezePane(0, CABECERA + 1);
        hoja.setAutoFilter(CABECERA, 0, COLUMNAS.size() - 1);
    }

    private static void texto(Worksheet hoja, int fila, int columna, String valor) {
        if (valor != null && !valor.isBlank() && !SIN_DATO.equals(valor)) {
            hoja.value(fila, columna, valor);
        }
    }
}
