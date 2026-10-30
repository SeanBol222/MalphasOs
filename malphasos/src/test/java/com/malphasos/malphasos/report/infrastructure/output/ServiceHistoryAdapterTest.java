package com.malphasos.malphasos.report.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El historial de la hoja de vida, derivado de los reportes cerrados desde {@code V52}: una línea por cada
 * par (orden, equipo), la de su último reporte cerrado —la regla de {@code V14}, que hasta entonces guardaba
 * la tabla {@code intervencion}—, de la más reciente a la más antigua.
 */
@ExtendWith(MockitoExtension.class)
class ServiceHistoryAdapterTest {

    private static final UUID EQUIPO = UUID.randomUUID();

    @Mock private ServiceReportPersistencePort serviceReportPersistencePort;

    @InjectMocks private ServiceHistoryAdapter adapter;

    private static ReportDocument documento(String tipoServicio, String ingeniero, List<ReportDocument.Paso> pasos) {
        ReportDocument d = UnDocumento.CUALQUIERA;
        ReportDocument.Orden o = d.orden();
        return new ReportDocument(d.version(), new ReportDocument.Orden(o.numero(), tipoServicio, o.garantia(),
                o.periodicidad(), o.fechaProgramada()), d.cliente(), d.equipo(), d.verificaciones(), d.empresa(),
                ingeniero, null, pasos, List.of());
    }

    /** Un reporte cerrado de ese equipo en esa orden, el día que se le diga, vigente o retirado. */
    private static ServiceReport cerrado(UUID orden, LocalDateTime cuando, ServiceResult resultado, boolean vigente) {
        return ServiceReport.rehydrate(UUID.randomUUID(), orden, EQUIPO, ReportState.FINALIZADO, null, null,
                "Limpieza general", null, null, null, List.of(), null, null, List.of(), null, resultado, cuando,
                List.of(), List.of(), vigente, documento("PREVENTIVO", "Ana Pérez", List.of()), false, null);
    }

    private static ServiceReport borrador(UUID orden) {
        return ServiceReport.open(orden, EQUIPO);
    }

    private static final LocalDateTime ENERO = LocalDateTime.of(2026, 1, 10, 9, 0);
    private static final LocalDateTime MARZO = LocalDateTime.of(2026, 3, 5, 15, 30);

    @Test
    @DisplayName("una linea por cada reporte cerrado, de la mas reciente a la mas antigua, y un borrador no es ninguna")
    void unaPorCierre() {
        ServiceReport primero = cerrado(UUID.randomUUID(), ENERO, ServiceResult.FUERA_DE_SERVICIO, true);
        ServiceReport segundo = cerrado(UUID.randomUUID(), MARZO, ServiceResult.OPERATIVO, true);
        when(serviceReportPersistencePort.findByEquipment(EQUIPO))
                .thenReturn(List.of(primero, borrador(UUID.randomUUID()), segundo));

        assertThat(adapter.historyOf(EQUIPO)).extracting(Intervention::idReporteServicio)
                .containsExactly(segundo.getId(), primero.getId());
    }

    @Test
    @DisplayName("sustituido y cerrado el sustituto, cuenta solo el sustituto")
    void elSustitutoReemplaza() {
        UUID orden = UUID.randomUUID();
        ServiceReport sustituido = cerrado(orden, ENERO, ServiceResult.FUERA_DE_SERVICIO, false);
        ServiceReport sustituto = cerrado(orden, MARZO, ServiceResult.OPERATIVO, true);
        when(serviceReportPersistencePort.findByEquipment(EQUIPO)).thenReturn(List.of(sustituto, sustituido));

        assertThat(adapter.historyOf(EQUIPO)).singleElement()
                .extracting(Intervention::idReporteServicio).isEqualTo(sustituto.getId());
    }

    @Test
    @DisplayName("mientras el sustituto es borrador, cuenta el sustituido aunque este retirado: el servicio ocurrio")
    void elSustituidoCuentaHastaQueSeCierre() {
        UUID orden = UUID.randomUUID();
        ServiceReport sustituido = cerrado(orden, ENERO, ServiceResult.OPERATIVO, false);
        when(serviceReportPersistencePort.findByEquipment(EQUIPO)).thenReturn(List.of(sustituido, borrador(orden)));

        assertThat(adapter.historyOf(EQUIPO)).singleElement()
                .extracting(Intervention::idReporteServicio).isEqualTo(sustituido.getId());
    }

    @Test
    @DisplayName("la linea dice el tipo y el ingeniero del documento y lo hecho del reporte")
    void loQueDiceLaLinea() {
        UUID limpieza = UUID.randomUUID();
        ServiceReport reporte = ServiceReport.rehydrate(UUID.randomUUID(), UUID.randomUUID(), EQUIPO,
                ReportState.FINALIZADO, null, null, "Cambio de pila", null, null, null, List.of(), null, null,
                List.of(), null, ServiceResult.OPERATIVO, MARZO, List.of(),
                List.of(new ReportStep(UUID.randomUUID(), limpieza, StepState.HECHO, null)), true,
                documento("CALIBRACION", "Ana Pérez",
                        List.of(new ReportDocument.Paso(limpieza, 1, "Limpieza", "Paño", null))),
                false, null);
        when(serviceReportPersistencePort.findByEquipment(EQUIPO)).thenReturn(List.of(reporte));

        Intervention linea = adapter.historyOf(EQUIPO).getFirst();

        assertThat(linea.tipoServicio()).isEqualTo(InterventionType.CALIBRACION);
        assertThat(linea.resultado()).isEqualTo(InterventionResult.OPERATIVO);
        assertThat(linea.fechaServicio()).isEqualTo(MARZO);
        assertThat(linea.responsable()).isEqualTo("Ana Pérez");
        assertThat(linea.descripcion()).isEqualTo("Limpieza. Además: Cambio de pila");
    }
}
