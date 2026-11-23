package com.malphasos.malphasos.orderworkbook;

import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.ORDEN;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.documento;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.reportes;
import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderSummaryComposer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El resumen de una orden en .xlsx (2026-11-22, v1.2.1, provisional): se escribe y se vuelve a leer, que es
 * lo que hará quien lo abra.
 */
class OrderSummaryWorkbookTest {

    private static List<Row> leer(ReportPdf archivo) throws IOException {
        try (ReadableWorkbook libro = new ReadableWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            return libro.getFirstSheet().read();
        }
    }

    private static WorkOrderSummarySheet resumen() {
        return WorkOrderSummaryComposer.compose(ORDEN, documento(LocalDate.of(2026, 10, 18)), reportes(),
                "Empresa de Prueba", null);
    }

    @Test
    @DisplayName("se llama como el PDF del resumen, con .xlsx")
    void elNombre() {
        assertThat(new OrderSummaryWorkbook().fileFor(resumen()).nombre()).isEqualTo("orden-1DC6F5.xlsx");
    }

    @Test
    @DisplayName("arriba la orden, el cliente, el ingeniero y los totales")
    void elEncabezado() throws IOException {
        List<Row> filas = leer(new OrderSummaryWorkbook().fileFor(resumen()));

        assertThat(filas.get(0).getCellAsString(0)).contains("Resumen de la orden 1DC6F5");
        assertThat(filas.get(1).getCellAsString(0).orElseThrow()).contains("Preventivo anual");
        assertThat(filas.get(2).getCellAsString(0).orElseThrow()).contains("Clínica de Ejemplo S.A.S.");
        assertThat(filas.get(3).getCellAsString(0)).contains("Ingeniero: Ingeniera de Prueba");
        assertThat(filas.get(4).getCellAsString(0).orElseThrow())
                .contains("Equipos: 3").contains("Fuera de servicio: 1");
    }

    @Test
    @DisplayName("una fila por equipo, con la marca y el modelo separados y el cierre como fecha")
    void lasFilas() throws IOException {
        List<Row> filas = leer(new OrderSummaryWorkbook().fileFor(resumen()));
        Row cabecera = filas.stream().filter(f -> f.getRowNum() == OrderSummaryWorkbook.CABECERA + 1)
                .findFirst().orElseThrow();
        List<Row> equipos = filas.stream().filter(f -> f.getRowNum() > OrderSummaryWorkbook.CABECERA + 1).toList();

        for (int columna = 0; columna < OrderSummaryWorkbook.COLUMNAS.size(); columna++) {
            assertThat(cabecera.getCellAsString(columna)).contains(OrderSummaryWorkbook.COLUMNAS.get(columna));
        }
        assertThat(equipos).hasSize(3);
        Row bomba = equipos.get(0);
        assertThat(bomba.getCellAsNumber(0)).contains(BigDecimal.ONE);
        assertThat(bomba.getCellAsString(1)).contains("Hospitalización");
        assertThat(bomba.getCellAsString(3)).contains("Marca B");
        assertThat(bomba.getCellAsString(4)).contains("Modelo B");
        assertThat(bomba.getCellAsString(7)).contains("CCCCCC");
        assertThat(bomba.getCellAsDate(8).orElseThrow().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(bomba.getCellAsString(9)).contains("Fuera de servicio");
        assertThat(bomba.getCellAsString(10)).contains("Luis Gómez");
    }

    @Test
    @DisplayName("lo que el PDF imprime con raya va vacío: el monitor sin modelo y la balanza que nadie recibió")
    void sinRayas() throws IOException {
        List<Row> equipos = leer(new OrderSummaryWorkbook().fileFor(resumen())).stream()
                .filter(f -> f.getRowNum() > OrderSummaryWorkbook.CABECERA + 1).toList();

        assertThat(equipos.get(2).getOptionalCell(4).map(c -> c.getRawValue())).isEmpty();
        assertThat(equipos.get(1).getOptionalCell(10).map(c -> c.getRawValue())).isEmpty();
    }
}
