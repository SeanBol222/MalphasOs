package com.malphasos.malphasos.report.infrastructure.input.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.InterventionRecordingPort;
import com.malphasos.malphasos.equipment.application.services.intervention.commands.RecordInterventionCommand;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportPayload;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * El primer consumidor de un evento de dominio de este sistema, probado por su cuenta.
 *
 * <p>Lo que se fija aquí es que la línea del historial se arma con datos de <b>dos</b> sitios: el
 * evento trae el equipo, la fecha de cierre y el resultado, y el <b>tipo de servicio no viene en el
 * evento</b> porque no es del reporte sino de la orden. Lo que no puede pasar desapercibido es que se
 * deje de consultar y la línea quede con un tipo que nadie eligió.
 */
@ExtendWith(MockitoExtension.class)
class ServiceReportFinishedListenerTest {

    private static final UUID REPORTE = UUID.randomUUID();
    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final LocalDateTime CERRADO = LocalDateTime.of(2026, 10, 4, 15, 30);

    @Mock private InterventionRecordingPort interventionRecordingPort;
    @Mock private WorkOrderServicePort workOrderServicePort;

    @InjectMocks private ServiceReportFinishedListener listener;

    private ServiceReportFinishedEvent elCierre(ServiceResult resultado) {
        return new ServiceReportFinishedEvent(
                EventMetadata.of("service-report", ServiceReportFinishedEvent.TYPE, REPORTE.toString()),
                new ServiceReportPayload(ORDEN, EQUIPO, ReportState.FINALIZADO, resultado, CERRADO));
    }

    private void laOrdenEsDeTipo(ServiceType tipo) {
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                .thenReturn(WorkOrder.schedule(
                        UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().plusDays(1),
                        Periodicity.ANUAL, tipo));
    }

    private RecordInterventionCommand loAnotado() {
        ArgumentCaptor<RecordInterventionCommand> comando =
                ArgumentCaptor.forClass(RecordInterventionCommand.class);
        verify(interventionRecordingPort).record(comando.capture());

        return comando.getValue();
    }

    @Test
    @DisplayName("al cerrarse un reporte se anota la intervencion con datos del evento y de la orden")
    void alCerrarseSeAnota() {
        laOrdenEsDeTipo(ServiceType.CALIBRACION);

        listener.onReportFinished(elCierre(ServiceResult.OPERATIVO_CON_RESTRICCIONES));

        RecordInterventionCommand anotado = loAnotado();
        assertThat(anotado.idEquipoCliente()).isEqualTo(EQUIPO);
        assertThat(anotado.idReporteServicio()).isEqualTo(REPORTE);
        assertThat(anotado.fechaServicio()).isEqualTo(CERRADO);
        assertThat(anotado.resultado()).isEqualTo(InterventionResult.OPERATIVO_CON_RESTRICCIONES);

        // El tipo NO viene en el evento: sale de la orden, y es el unico dato que cuesta una consulta.
        assertThat(anotado.tipoServicio()).isEqualTo(InterventionType.CALIBRACION);
    }

    @Test
    @DisplayName("la fecha de la intervencion es la del cierre, no la programada en la orden")
    void laFechaEsLaDelCierre() {
        // Un mantenimiento se ejecuta el dia que se ejecuta. La orden esta programada para manana y
        // el cierre es del 4 de octubre: lo que llega al historial es el cierre.
        laOrdenEsDeTipo(ServiceType.PREVENTIVO);

        listener.onReportFinished(elCierre(ServiceResult.OPERATIVO));

        assertThat(loAnotado().fechaServicio()).isEqualTo(CERRADO);
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("los tres tipos de servicio de la orden llegan al historial")
    void losTresTipos() {
        for (ServiceType tipo : ServiceType.values()) {
            laOrdenEsDeTipo(tipo);
            listener.onReportFinished(elCierre(ServiceResult.OPERATIVO));
        }

        ArgumentCaptor<RecordInterventionCommand> comando =
                ArgumentCaptor.forClass(RecordInterventionCommand.class);
        verify(interventionRecordingPort, times(ServiceType.values().length)).record(comando.capture());

        assertThat(comando.getAllValues())
                .extracting(c -> c.tipoServicio().name())
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ServiceType.values()).map(Enum::name).toList());
    }
}
