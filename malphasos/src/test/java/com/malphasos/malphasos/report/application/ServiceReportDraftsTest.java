package com.malphasos.malphasos.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.ServiceReportDrafts;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El borrador que pone un equipo en una orden (2026-10-28, {@code V50}). Estas pruebas estaban en
 * {@code ServiceReportServiceTest}, bajo «Al abrir un reporte», hasta que abrir dejó de ser una operación
 * del servicio y pasó a nacer al añadir el equipo y al sustituir un reporte cerrado.
 */
@ExtendWith(MockitoExtension.class)
class ServiceReportDraftsTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID MODELO = UUID.randomUUID();
    private static final UUID EQUIPO_CATALOGO = UUID.randomUUID();
    private static final UUID TIPO = UUID.randomUUID();

    @Mock private ServiceReportPersistencePort serviceReportPersistencePort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private ModelServicePort modelServicePort;
    @Mock private EquipmentServicePort equipmentServicePort;
    @Mock private ProtocolServicePort protocolServicePort;
    @Mock private EventDispatcherPort eventDispatcherPort;

    @InjectMocks private ServiceReportDrafts drafts;

    /** La cadena del catálogo hasta el tipo, y los protocolos de ese tipo. */
    private void estubarProtocolos(AssignedProtocolDetail... protocolos) {
        when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(
                ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, UUID.randomUUID(), null, null, null, null, null,
                        null, true));
        when(modelServicePort.findById(MODELO)).thenReturn(
                Model.rehydrate(MODELO, "IdeaPad 3", null, UUID.randomUUID(), EQUIPO_CATALOGO, TechnicalSheet.EMPTY, true));
        when(equipmentServicePort.findById(EQUIPO_CATALOGO)).thenReturn(
                Equipment.rehydrate(EQUIPO_CATALOGO, TIPO, UUID.randomUUID(), true));
        when(protocolServicePort.protocolsOf(TIPO)).thenReturn(List.of(protocolos));
    }

    private void estubarGuardado() {
        when(serviceReportPersistencePort.save(any(ServiceReport.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));
    }

    @Test
    @DisplayName("abre un borrador de ese equipo en esa orden, lo guarda y lo anuncia")
    void abreElBorrador() {
        estubarProtocolos();
        estubarGuardado();

        ServiceReport reporte = drafts.open(ORDEN, ServiceType.PREVENTIVO, EQUIPO);

        assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
        assertThat(reporte.getIdOrdenTrabajo()).isEqualTo(ORDEN);
        assertThat(reporte.getIdEquipoCliente()).isEqualTo(EQUIPO);
        verify(eventDispatcherPort).dispatch(any());
    }

    @Test
    @DisplayName("si el numero ya lo tiene otro reporte, elige otro identificador y no publica el descartado")
    void numeroTomado() {
        estubarProtocolos();
        estubarGuardado();
        List<String> consultados = new ArrayList<>();
        when(serviceReportPersistencePort.numberTaken(any())).thenAnswer(llamada -> {
            consultados.add(llamada.getArgument(0));
            return consultados.size() == 1;
        });

        ServiceReport reporte = drafts.open(ORDEN, ServiceType.PREVENTIVO, EQUIPO);

        assertThat(consultados).hasSize(2);
        assertThat(reporte.numero()).isEqualTo(consultados.get(1)).isNotEqualTo(consultados.get(0));
        // Un solo evento: el del reporte guardado. El descartado no ocurrio.
        verify(eventDispatcherPort).dispatch(any());
    }

    @Test
    @DisplayName("si nunca encuentra un numero libre, se rinde sin guardar nada")
    void sinNumeroLibre() {
        estubarProtocolos();
        when(serviceReportPersistencePort.numberTaken(any())).thenReturn(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> drafts.open(ORDEN, ServiceType.PREVENTIVO, EQUIPO))
                .withMessageContaining("numero de reporte libre");
        verify(serviceReportPersistencePort, times(10)).numberTaken(any());
        verify(serviceReportPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("en un preventivo copia los protocolos del tipo como pasos pendientes, en su orden")
    void copiaLosPasos() {
        // RF-13: se cargan solos. Se copian el nombre, la descripcion y la indicacion de ese dia.
        Protocol limpieza = Protocol.create("Limpieza", "Paño humedecido.");
        Protocol electrico = Protocol.create("Eléctrico", "Multímetro.");
        estubarProtocolos(
                new AssignedProtocolDetail(limpieza, 1, "Con guantes"),
                new AssignedProtocolDetail(electrico, 2, null));
        estubarGuardado();

        ServiceReport reporte = drafts.open(ORDEN, ServiceType.PREVENTIVO, EQUIPO);

        assertThat(reporte.getPasos())
                .extracting(ReportStep::orden, ReportStep::nombre, ReportStep::indicacion, ReportStep::estado)
                .containsExactly(
                        tuple(1, "Limpieza", "Con guantes", StepState.PENDIENTE),
                        tuple(2, "Eléctrico", null, StepState.PENDIENTE));
    }

    @Test
    @DisplayName("en un correctivo no copia pasos, ni siquiera pregunta por ellos")
    void correctivoSinPasos() {
        // Decidido por el usuario: un correctivo es una reparacion distinta cada vez. Los protocolos se
        // dejan estubados para que, si la regla faltara, la prueba caiga por tener pasos.
        lenient().when(protocolServicePort.protocolsOf(any()))
                .thenReturn(List.of(new AssignedProtocolDetail(Protocol.create("Limpieza", "x"), 1, null)));
        lenient().when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(
                ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, UUID.randomUUID(), null, null, null, null, null,
                        null, true));
        lenient().when(modelServicePort.findById(MODELO)).thenReturn(
                Model.rehydrate(MODELO, "IdeaPad 3", null, UUID.randomUUID(), EQUIPO_CATALOGO, TechnicalSheet.EMPTY, true));
        lenient().when(equipmentServicePort.findById(EQUIPO_CATALOGO)).thenReturn(
                Equipment.rehydrate(EQUIPO_CATALOGO, TIPO, UUID.randomUUID(), true));
        estubarGuardado();

        ServiceReport reporte = drafts.open(ORDEN, ServiceType.CORRECTIVO, EQUIPO);

        assertThat(reporte.getPasos()).isEmpty();
        verify(protocolServicePort, never()).protocolsOf(any());
    }
}
