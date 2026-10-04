package com.malphasos.malphasos.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.ServiceReportService;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las tres lecturas de reportes acotan por dueño, y la del identificador tiene el único caso donde
 * delegar sería <b>incorrecto</b>.
 *
 * <p>Un reporte no guarda su cliente: guarda su orden, y la orden guarda el cliente. Delegar —pedir
 * la orden con el alcance— habría sido lo breve, y el error que sale de ahí es «esa orden no existe»
 * cuando lo que se pidió fue un reporte. La respuesta estaría contando de qué es el identificador
 * que no se puede ver, que es justo lo que las cuatro tandas de este filtro han evitado. Se resuelve
 * el dueño con alcance libre y se lanza el error del recurso pedido.
 */
@ExtendWith(MockitoExtension.class)
class ReportOwnershipFilteringTest {

    private static final UUID MIO = UUID.randomUUID();
    private static final UUID AJENO = UUID.randomUUID();
    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();

    private static final ReadScope SOLO_MIO = ReadScope.ofClients(Set.of(MIO));

    @Mock private ServiceReportPersistencePort serviceReportPersistencePort;
    @Mock private WorkOrderServicePort workOrderServicePort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private ModelServicePort modelServicePort;
    @Mock private EquipmentServicePort equipmentServicePort;
    @Mock private EquipmentTypeServicePort equipmentTypeServicePort;
    @Mock private EventDispatcherPort eventDispatcherPort;

    @InjectMocks private ServiceReportService service;

    private WorkOrder unaOrden(UUID idCliente) {
        return WorkOrder.schedule(
                idCliente, SEDE, LocalDate.now().plusDays(1), Periodicity.ANUAL, ServiceType.PREVENTIVO);
    }

    @Test
    @DisplayName("los reportes de una orden ajena los rechaza el modulo de ordenes, con el alcance")
    void reportesDeOrdenAjena() {
        when(workOrderServicePort.findById(ORDEN, SOLO_MIO)).thenThrow(new WorkOrderNotFoundException(ORDEN));

        assertThatThrownBy(() -> service.findByWorkOrder(ORDEN, SOLO_MIO))
                .isInstanceOf(WorkOrderNotFoundException.class);

        verify(serviceReportPersistencePort, never()).findByWorkOrder(any());
    }

    @Test
    @DisplayName("los reportes de la orden propia se devuelven, y se le pasa el alcance")
    void reportesDeOrdenPropia() {
        when(serviceReportPersistencePort.findByWorkOrder(ORDEN))
                .thenReturn(List.of(ServiceReport.open(ORDEN, EQUIPO)));

        assertThat(service.findByWorkOrder(ORDEN, SOLO_MIO)).hasSize(1);

        // Con unrestricted() esto compilaria y no filtraria nada: lo que se fija es que viaje.
        verify(workOrderServicePort).findById(ORDEN, SOLO_MIO);
    }

    @Test
    @DisplayName("el historial de un equipo ajeno lo rechaza el modulo de equipos, con el alcance")
    void historialDeEquipoAjeno() {
        when(clientEquipmentServicePort.findById(EQUIPO, SOLO_MIO))
                .thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> service.findByEquipment(EQUIPO, SOLO_MIO))
                .isInstanceOf(ClientEquipmentNotFoundException.class);

        verify(serviceReportPersistencePort, never()).findByEquipment(any());
    }

    @Test
    @DisplayName("el historial del equipo propio se devuelve, y se le pasa el alcance")
    void historialDeEquipoPropio() {
        when(serviceReportPersistencePort.findByEquipment(EQUIPO))
                .thenReturn(List.of(ServiceReport.open(ORDEN, EQUIPO)));

        assertThat(service.findByEquipment(EQUIPO, SOLO_MIO)).hasSize(1);
        verify(clientEquipmentServicePort).findById(EQUIPO, SOLO_MIO);
    }

    @Test
    @DisplayName("un reporte de otro cliente no existe, y el error es de reporte, no de orden")
    void reporteDeOtroCliente() {
        ServiceReport ajeno = ServiceReport.open(ORDEN, EQUIPO);
        when(serviceReportPersistencePort.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrden(AJENO));

        // Que el tipo de la excepcion sea este es el contenido de la prueba. Delegando saldria
        // WorkOrderNotFoundException, y eso le diria a quien pregunta que ese identificador es de un
        // reporte cuya orden no puede ver, en vez de no decirle nada.
        assertThatThrownBy(() -> service.findById(ajeno.getId(), SOLO_MIO))
                .isInstanceOf(ServiceReportNotFoundException.class);
    }

    @Test
    @DisplayName("el reporte propio se devuelve")
    void reportePropio() {
        ServiceReport mio = ServiceReport.open(ORDEN, EQUIPO);
        when(serviceReportPersistencePort.findById(mio.getId())).thenReturn(Optional.of(mio));
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrden(MIO));

        assertThat(service.findById(mio.getId(), SOLO_MIO).getId()).isEqualTo(mio.getId());
    }

    @Test
    @DisplayName("sin restriccion no se consulta la orden para saber de quien es el reporte")
    void sinRestriccionNoSeResuelveElDueno() {
        ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);
        when(serviceReportPersistencePort.findById(reporte.getId())).thenReturn(Optional.of(reporte));

        assertThat(service.findById(reporte.getId(), ReadScope.unrestricted()).getId())
                .isEqualTo(reporte.getId());

        // Sin el atajo, abrir cualquier reporte costaria una consulta mas para todo el mundo.
        verify(workOrderServicePort, never()).findById(any(), any());
    }
}
