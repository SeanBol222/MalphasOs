package com.malphasos.malphasos.report.application.sheet;

import static com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet.Marca.NADA;
import static com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet.Marca.PROGRAMADO;
import static com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet.Marca.REALIZADO;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.ORDEN;
import static com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.reportes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderScheduleComposer;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que dice el cronograma de una orden (2026-10-31), antes de pintarlo. */
class WorkOrderScheduleComposerTest {

    private static final DocumentImage LOGO =
            new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1}, "image/png");

    /** La orden de las pruebas con otra periodicidad y otra fecha de ejecución. */
    private static WorkOrderDocument orden(String periodicidad, LocalDate ejecutada) {
        WorkOrderDocument base = UnaOrdenAtendida.documento(ejecutada);

        return new WorkOrderDocument(base.version(), base.numero(), base.tipoServicio(), base.garantia(),
                periodicidad, base.fechaMantenimiento(), ejecutada, base.cliente(), base.sede(), base.ingeniero(),
                base.equipos());
    }

    private static WorkOrderScheduleSheet componer(String periodicidad, LocalDate ejecutada) {
        return WorkOrderScheduleComposer.compose(ORDEN, orden(periodicidad, ejecutada), reportes(), "Empresa", LOGO);
    }

    private static List<WorkOrderScheduleSheet.Marca> cada(int meses) {
        List<WorkOrderScheduleSheet.Marca> marcas = new java.util.ArrayList<>(Collections.nCopies(13, NADA));
        marcas.set(0, REALIZADO);
        for (int i = meses; i <= 12; i += meses) {
            marcas.set(i, PROGRAMADO);
        }

        return marcas;
    }

    @Test
    @DisplayName("una fila por equipo en el orden del documento, y una sola fecha para toda la orden")
    void lasFilas() {
        WorkOrderScheduleSheet hoja = componer("SEMESTRAL", LocalDate.of(2026, 10, 18));

        assertThat(hoja.filas()).extracting(WorkOrderScheduleSheet.Fila::n).containsExactly(1, 2, 3);
        assertThat(hoja.filas()).extracting(WorkOrderScheduleSheet.Fila::area)
                .containsExactly("Hospitalización", "Laboratorio", "Urgencias");
        WorkOrderScheduleSheet.Fila balanza = hoja.filas().get(1);
        assertThat(balanza.equipo()).isEqualTo("Balanza");
        assertThat(balanza.marcaModelo()).isEqualTo("Kern PCB");
        assertThat(balanza.serie()).isEqualTo("SN-3");
        assertThat(balanza.hojaDeVida()).isEqualTo("HV-CLE-0003");
        // Los reportes se cerraron el 16; la fecha es la de la orden, el 18, para todos.
        assertThat(balanza.proximo()).isEqualTo("18 abr 2027");
        assertThat(balanza.fueraDeServicio()).isFalse();
        assertThat(hoja.filas().get(2).proximo()).isEqualTo("18 abr 2027");
        assertThat(hoja.realizado()).isEqualTo("18 oct 2026");
        assertThat(hoja.proximo()).isEqualTo("18 abr 2027");
        assertThat(hoja.periodicidad()).isEqualTo("semestral");
    }

    @Test
    @DisplayName("un equipo fuera de servicio no se programa: lo dice en vez de la fecha")
    void fueraDeServicio() {
        WorkOrderScheduleSheet.Fila bomba = componer("SEMESTRAL", LocalDate.of(2026, 10, 18)).filas().get(0);

        assertThat(bomba.equipo()).isEqualTo("Bomba de infusión");
        assertThat(bomba.proximo()).isEqualTo("Fuera de servicio");
        assertThat(bomba.fueraDeServicio()).isTrue();
        assertThat(bomba.marcas()).containsExactlyElementsOf(cada(13));
    }

    @Test
    @DisplayName("las marcas siguen la periodicidad: realizado en el primer mes y programado cada tantos")
    void lasMarcas() {
        LocalDate dia = LocalDate.of(2026, 10, 18);

        assertThat(componer("MENSUAL", dia).filas().get(1).marcas()).containsExactlyElementsOf(cada(1));
        assertThat(componer("TRIMESTRAL", dia).filas().get(1).marcas()).containsExactlyElementsOf(cada(3));
        assertThat(componer("SEMESTRAL", dia).filas().get(1).marcas()).containsExactlyElementsOf(cada(6));
        assertThat(componer("ANUAL", dia).filas().get(1).marcas()).containsExactlyElementsOf(cada(12));
    }

    @Test
    @DisplayName("el 31 de agosto más seis meses es el 28 de febrero, y la marca cae en febrero")
    void finDeMes() {
        WorkOrderScheduleSheet hoja = componer("SEMESTRAL", LocalDate.of(2026, 8, 31));

        assertThat(hoja.proximo()).isEqualTo("28 feb 2027");
        assertThat(hoja.filas().get(1).marcas()).containsExactlyElementsOf(cada(6));
        assertThat(hoja.meses().get(6).nombre()).isEqualTo("feb");
    }

    @Test
    @DisplayName("trece meses desde el del servicio, con el año en el primero y en enero")
    void losMeses() {
        List<WorkOrderScheduleSheet.Mes> meses = componer("ANUAL", LocalDate.of(2026, 10, 18)).meses();

        assertThat(meses).extracting(WorkOrderScheduleSheet.Mes::nombre).containsExactly(
                "oct", "nov", "dic", "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct");
        assertThat(meses).extracting(WorkOrderScheduleSheet.Mes::anio).containsExactly(
                "2026", null, null, "2027", null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("sin fecha de ejecución en el documento, cuenta desde la programada")
    void sinEjecucion() {
        WorkOrderScheduleSheet hoja = componer("ANUAL", null);

        assertThat(hoja.realizado()).isEqualTo("14 oct 2026");
        assertThat(hoja.proximo()).isEqualTo("14 oct 2027");
    }

    @Test
    @DisplayName("la cabecera es la del resumen: la orden, el servicio, el cliente y la empresa con su logo")
    void laCabecera() {
        WorkOrderScheduleSheet hoja = componer("SEMESTRAL", LocalDate.of(2026, 10, 18));

        assertThat(hoja.servicio()).isEqualTo("Orden N.º 1DC6F5 · Preventivo semestral · Ejecutada el 18 oct 2026");
        assertThat(hoja.cliente()).isEqualTo("Clínica de Ejemplo S.A.S. · NIT 900000000-0 · Sede Norte, Bogotá");
        assertThat(hoja.ingeniero()).isEqualTo("Ingeniera de Prueba");
        assertThat(hoja.empresa()).isEqualTo("Empresa");
        assertThat(hoja.logo()).isEqualTo(LOGO.dataUri());
        assertThat(hoja.idOrden()).isEqualTo(ORDEN);
        assertThat(hoja.numero()).isEqualTo("1DC6F5");
    }

    @Test
    @DisplayName("una orden sin periodicidad no se programa")
    void sinPeriodicidad() {
        assertThat(WorkOrderScheduleComposer.applies(orden(null, LocalDate.of(2026, 10, 18)))).isFalse();
        assertThat(WorkOrderScheduleComposer.applies(orden("ANUAL", LocalDate.of(2026, 10, 18)))).isTrue();
        // El documento congelado guarda la periodicidad como texto: una que no conoce no se programa.
        assertThat(WorkOrderScheduleComposer.applies(orden("QUINCENAL", LocalDate.of(2026, 10, 18)))).isFalse();
        assertThatThrownBy(() -> componer(null, LocalDate.of(2026, 10, 18)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene periodicidad");
    }

    @Test
    @DisplayName("un equipo del documento sin reporte activo no entra: se retiró de la orden")
    void sinReporte() {
        WorkOrderScheduleSheet hoja = WorkOrderScheduleComposer.compose(ORDEN,
                orden("ANUAL", LocalDate.of(2026, 10, 18)), reportes().subList(0, 2), null, null);

        assertThat(hoja.filas()).extracting(WorkOrderScheduleSheet.Fila::equipo).containsExactly("Balanza", "Monitor");
        assertThat(hoja.filas()).extracting(WorkOrderScheduleSheet.Fila::n).containsExactly(1, 2);
        assertThat(hoja.logo()).isNull();
    }
}
