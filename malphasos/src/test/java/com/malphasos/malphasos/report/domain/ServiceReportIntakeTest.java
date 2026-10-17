package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportHandedOverEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportIntakeRecordedEvent;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Cómo llegó el equipo y a quién se entregó (V31, 2026-10-16). */
class ServiceReportIntakeTest {

    private static final IntakeCheck CONFORME = new IntakeCheck(IntakeState.CONFORME, null);

    private static ServiceReport unReporte() {
        ServiceReport reporte = ServiceReport.open(UUID.randomUUID(), UUID.randomUUID());
        reporte.pullEvents();
        return reporte;
    }

    private static ServiceReport unReporteCerrable() {
        ServiceReport reporte = unReporte();
        reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
        reporte.pullEvents();
        return reporte;
    }

    private static ServiceReport unReporteCerrado() {
        ServiceReport reporte = unReporteCerrable();
        reporte.recordIntake(CONFORME, CONFORME, List.of());
        reporte.finish();
        return reporte;
    }

    private static List<String> tiposDe(ServiceReport reporte) {
        return reporte.pullEvents().stream().map(DomainEvent::metadata).map(m -> m.eventType()).toList();
    }

    @Nested
    @DisplayName("Las verificaciones al ingreso y los accesorios")
    class AlIngreso {

        @Test
        @DisplayName("se registran juntas y lo anuncian; registrar lo mismo otra vez no")
        void registrar() {
            ServiceReport reporte = unReporte();
            IntakeCheck rayado = new IntakeCheck(IntakeState.NO_CONFORME, " Carcasa rayada ");

            reporte.recordIntake(rayado, CONFORME, List.of(ReportAccessory.of(1, "Sonda", true, null)));
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportIntakeRecordedEvent.TYPE);
            assertThat(reporte.getIngresoFisico().observacion()).isEqualTo("Carcasa rayada");
            assertThat(reporte.getIngresoOperativo()).isEqualTo(CONFORME);
            assertThat(reporte.accesoriosActivos()).extracting(ReportAccessory::nombre).containsExactly("Sonda");

            reporte.recordIntake(new IntakeCheck(IntakeState.NO_CONFORME, "Carcasa rayada"), CONFORME,
                    List.of(ReportAccessory.of(1, "Sonda", true, null)));
            assertThat(tiposDe(reporte)).isEmpty();
        }

        @Test
        @DisplayName("cambiar solo un accesorio, o solo una verificacion, si es un cambio")
        void cambiosSueltos() {
            ServiceReport reporte = unReporte();
            reporte.recordIntake(CONFORME, CONFORME, List.of(ReportAccessory.of(1, "Sonda", true, null)));
            reporte.pullEvents();

            reporte.recordIntake(CONFORME, CONFORME, List.of(ReportAccessory.of(1, "Sonda", false, "Pelada")));
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportIntakeRecordedEvent.TYPE);

            reporte.recordIntake(CONFORME, new IntakeCheck(IntakeState.NO_APLICA, null),
                    List.of(ReportAccessory.of(1, "Sonda", false, "Pelada")));
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportIntakeRecordedEvent.TYPE);
        }

        @Test
        @DisplayName("los accesorios nuevos sustituyen a los anteriores, que se retiran sin borrarse")
        void sustituir() {
            ServiceReport reporte = unReporte();
            reporte.recordIntake(CONFORME, CONFORME, List.of(
                    ReportAccessory.of(1, "Sonda", true, null), ReportAccessory.of(2, "Cable", true, null)));

            reporte.recordIntake(CONFORME, CONFORME, List.of(ReportAccessory.of(1, "Brazalete", true, null)));

            assertThat(reporte.accesoriosActivos()).extracting(ReportAccessory::nombre)
                    .containsExactly("Brazalete");
            assertThat(reporte.getAccesorios()).hasSize(3)
                    .filteredOn(accesorio -> !accesorio.estadoActivo())
                    .extracting(ReportAccessory::nombre).containsExactlyInAnyOrder("Sonda", "Cable");
        }

        @Test
        @DisplayName("sin accesorios tambien se registra: muchos equipos llegan solos")
        void sinAccesorios() {
            ServiceReport reporte = unReporte();
            reporte.recordIntake(CONFORME, CONFORME, List.of(ReportAccessory.of(1, "Sonda", true, null)));

            reporte.recordIntake(CONFORME, CONFORME, List.of());

            assertThat(reporte.accesoriosActivos()).isEmpty();
        }

        @Test
        @DisplayName("salen en su orden, no en el que se registraron")
        void enSuOrden() {
            ServiceReport reporte = unReporte();

            reporte.recordIntake(CONFORME, CONFORME, List.of(
                    ReportAccessory.of(2, "Cable", true, null), ReportAccessory.of(1, "Sonda", true, null)));

            assertThat(reporte.accesoriosActivos()).extracting(ReportAccessory::nombre)
                    .containsExactly("Sonda", "Cable");
        }

        @Test
        @DisplayName("las dos verificaciones son obligatorias, y dos accesorios no comparten orden")
        void obligatorias() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> unReporte().recordIntake(null, CONFORME, List.of()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> unReporte().recordIntake(CONFORME, null, List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> unReporte().recordIntake(CONFORME, CONFORME,
                    List.of(ReportAccessory.of(1, "Sonda", true, null), ReportAccessory.of(1, "Cable", true, null))));
        }

        @Test
        @DisplayName("no conforme exige observacion, en la verificacion y en el accesorio; no aplica no")
        void noConformeDiceQue() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new IntakeCheck(IntakeState.NO_CONFORME, "  "))
                    .withMessageContaining("observacion");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ReportAccessory.of(1, "Sonda", false, null))
                    .withMessageContaining("Sonda");
            assertThat(new IntakeCheck(IntakeState.NO_APLICA, " ").observacion()).isNull();
            assertThatIllegalArgumentException().isThrownBy(() -> new IntakeCheck(null, "x"));
        }

        @Test
        @DisplayName("en un reporte cerrado no se registra")
        void soloEnBorrador() {
            // El cierre va fuera de la lambda: dentro, si cerrar lanzara, la prueba pasaría por ese camino.
            ServiceReport cerrado = unReporteCerrado();

            assertThatIllegalStateException()
                    .isThrownBy(() -> cerrado.recordIntake(CONFORME, CONFORME, List.of()))
                    .withMessageContaining("ya cerrado");
        }
    }

    @Nested
    @DisplayName("Quien recibe")
    class QuienRecibe {

        @Test
        @DisplayName("se registra recortado y lo anuncia; decirlo otra vez igual no")
        void registrar() {
            ServiceReport reporte = unReporte();

            reporte.recordHandover(new Handover(" Ana Pérez ", "52123456", " Jefe de enfermeria "));
            assertThat(reporte.getRecibe()).isEqualTo(new Handover("Ana Pérez", "52123456", "Jefe de enfermeria"));
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportHandedOverEvent.TYPE);

            reporte.recordHandover(new Handover("Ana Pérez", "52123456", "Jefe de enfermeria"));
            assertThat(tiposDe(reporte)).isEmpty();
        }

        @Test
        @DisplayName("con nulo se borra, y eso tambien se anuncia")
        void borrar() {
            ServiceReport reporte = unReporte();
            reporte.recordHandover(new Handover("Ana Pérez", null, null));
            reporte.pullEvents();

            reporte.recordHandover(null);

            assertThat(reporte.getRecibe()).isNull();
            assertThat(tiposDe(reporte)).containsExactly(ServiceReportHandedOverEvent.TYPE);
        }

        @Test
        @DisplayName("cedula y cargo son opcionales; el nombre no")
        void elNombreNo() {
            assertThat(new Handover("Ana", " ", null).cedula()).isNull();
            assertThatIllegalArgumentException().isThrownBy(() -> new Handover(" ", "52123456", "Jefe"));
            assertThatIllegalArgumentException().isThrownBy(() -> new Handover("Ana", "1".repeat(21), null));
        }

        @Test
        @DisplayName("en un reporte cerrado no se registra")
        void soloEnBorrador() {
            ServiceReport cerrado = unReporteCerrado();

            assertThatIllegalStateException()
                    .isThrownBy(() -> cerrado.recordHandover(new Handover("Ana", null, null)))
                    .withMessageContaining("ya cerrado");
        }
    }

    @Nested
    @DisplayName("Al cerrar (diseño único del informe, 2026-10-16)")
    class AlCerrar {

        @Test
        @DisplayName("sin las verificaciones al ingreso no se cierra: el informe no diría de dónde se partió")
        void sinIngresoNoSeCierra() {
            ServiceReport reporte = unReporteCerrable();

            assertThatIllegalStateException()
                    .isThrownBy(reporte::finish)
                    .withMessageContaining("verificaciones al ingreso");
            assertThat(reporte.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("un correctivo tampoco, aunque traiga falla y diagnóstico")
        void tampocoUnCorrectivo() {
            ServiceReport reporte = unReporteCerrable();
            reporte.fill("No enciende", "Fusible abierto", "Cambio de fusible", null, ServiceResult.OPERATIVO);

            assertThatIllegalStateException()
                    .isThrownBy(reporte::finishCorrective)
                    .withMessageContaining("verificaciones al ingreso");
        }

        @Test
        @DisplayName("con ellas se cierra, aunque nadie haya firmado que lo recibe: eso va a mano")
        void conIngresoSinQuienRecibe() {
            ServiceReport reporte = unReporteCerrable();
            reporte.recordIntake(CONFORME, new IntakeCheck(IntakeState.NO_APLICA, null), List.of());

            reporte.finish();

            assertThat(reporte.getRecibe()).isNull();
        }
    }
}
