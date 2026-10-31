package com.malphasos.malphasos.report.application.sheet;

import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.BALANZA;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.ORDEN;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.cerrado;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.documento;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.reportes;
import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderSummaryComposer;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que dice el resumen de una orden (2026-10-31), antes de pintarlo. */
class WorkOrderSummaryComposerTest {

    private static final DocumentImage LOGO =
            new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1}, "image/png");

    private static WorkOrderSummarySheet componer(LocalDate ejecutada) {
        return WorkOrderSummaryComposer.compose(ORDEN, documento(ejecutada), reportes(), "Empresa de Prueba", LOGO);
    }

    @Test
    @DisplayName("una fila por equipo en el orden del documento, con el informe vigente y quién lo recibió")
    void lasFilas() {
        WorkOrderSummarySheet hoja = componer(LocalDate.of(2026, 10, 18));

        assertThat(hoja.filas()).extracting(WorkOrderSummarySheet.Fila::n).containsExactly(1, 2, 3);
        assertThat(hoja.filas()).extracting(WorkOrderSummarySheet.Fila::area)
                .containsExactly("Hospitalización", "Laboratorio", "Urgencias");
        WorkOrderSummarySheet.Fila bomba = hoja.filas().get(0);
        // El documento guardó VIEJO1; vale el reporte activo, que lo sustituyó.
        assertThat(bomba.informe()).isEqualTo("CCCCCC");
        assertThat(bomba.equipo()).isEqualTo("Bomba de infusión");
        assertThat(bomba.marcaModelo()).isEqualTo("Marca B Modelo B");
        assertThat(bomba.serie()).isEqualTo("SN-2");
        assertThat(bomba.hojaDeVida()).isEqualTo("HV-CLE-0002");
        assertThat(bomba.cerrado()).isEqualTo("16 oct 2026");
        assertThat(bomba.resultado()).isEqualTo("Fuera de servicio");
        assertThat(bomba.recibio()).isEqualTo("Luis Gómez");
        assertThat(bomba.destacada()).isTrue();
        WorkOrderSummarySheet.Fila balanza = hoja.filas().get(1);
        assertThat(balanza.recibio()).isEqualTo("—");
        assertThat(balanza.destacada()).isFalse();
        assertThat(hoja.filas().get(2).marcaModelo()).isEqualTo("Mindray");
    }

    @Test
    @DisplayName("los totales cuentan los reportes por resultado")
    void losTotales() {
        assertThat(componer(LocalDate.of(2026, 10, 18)).totales())
                .isEqualTo(new WorkOrderSummarySheet.Totales(3, 2, 0, 1));
    }

    @Test
    @DisplayName("la cabecera dice la orden, el servicio, cuándo se ejecutó, el cliente y la empresa con su logo")
    void laCabecera() {
        WorkOrderSummarySheet hoja = componer(LocalDate.of(2026, 10, 18));

        assertThat(hoja.servicio()).isEqualTo("Orden N.º 1DC6F5 · Preventivo anual · Ejecutada el 18 oct 2026");
        assertThat(hoja.cliente()).isEqualTo("Clínica de Ejemplo S.A.S. · NIT 900000000-0 · Sede Norte, Bogotá");
        assertThat(hoja.ingeniero()).isEqualTo("Ingeniera de Prueba");
        assertThat(hoja.empresa()).isEqualTo("Empresa de Prueba");
        assertThat(hoja.logo()).isEqualTo(LOGO.dataUri());
        assertThat(hoja.idOrden()).isEqualTo(ORDEN);
    }

    @Test
    @DisplayName("una orden sin ejecutar dice que está en curso, y una en garantía lo dice")
    void enCursoYGarantia() {
        WorkOrderDocument base = documento(null);
        WorkOrderDocument enGarantia = new WorkOrderDocument(base.version(), base.numero(), "CORRECTIVO", true,
                "SEMESTRAL", base.fechaMantenimiento(), null, base.cliente(), base.sede(), null, base.equipos());

        WorkOrderSummarySheet hoja =
                WorkOrderSummaryComposer.compose(ORDEN, enGarantia, reportes(), null, null);

        assertThat(hoja.servicio()).isEqualTo("Orden N.º 1DC6F5 · Correctivo semestral en garantía · En curso");
        assertThat(hoja.ingeniero()).isEqualTo("—");
        assertThat(hoja.logo()).isNull();
    }

    @Test
    @DisplayName("un equipo del documento sin reporte activo no sale: se quitó de la orden")
    void sinReporte() {
        WorkOrderSummarySheet hoja = WorkOrderSummaryComposer.compose(ORDEN, documento(null),
                List.of(cerrado("bbbbbb00-0000-4000-8000-000000000000", BALANZA, ServiceResult.OPERATIVO_CON_RESTRICCIONES,
                        null)),
                "Empresa", null);

        assertThat(hoja.filas()).extracting(WorkOrderSummarySheet.Fila::equipo).containsExactly("Balanza");
        assertThat(hoja.filas().get(0).n()).isEqualTo(1);
        assertThat(hoja.filas().get(0).destacada()).isTrue();
        assertThat(hoja.totales()).isEqualTo(new WorkOrderSummarySheet.Totales(1, 0, 1, 0));
    }
}
