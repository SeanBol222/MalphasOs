package com.malphasos.malphasos.workorder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderStandardsPersistencePort;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.WorkOrderStandardsCatalog;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.WorkOrderStandardsService;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands.AssignWorkOrderStandardsCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lo que el esquema no expresa de los patrones de una orden (V60): el tipo tiene que tener equipos en ella,
 * cada patrón existir, estar activo y caber en alguna verificación del tipo, y la orden no estar ejecutada.
 * Y que el cambio llegue a sus reportes.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderStandardsServiceTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID TIPO = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();

    @Mock private WorkOrderStandardsPersistencePort workOrderStandardsPersistencePort;
    @Mock private WorkOrderServicePort workOrderServicePort;
    @Mock private WorkOrderReportsPort workOrderReportsPort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private ReferenceStandardServicePort referenceStandardServicePort;
    @Mock private WorkOrderStandardsCatalog workOrderStandardsCatalog;
    @Mock private EventDispatcherPort eventDispatcherPort;

    @InjectMocks private WorkOrderStandardsService service;

    private static WorkOrder unaOrden(ExecutionState estado) {
        return WorkOrder.rehydrate(ORDEN, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), Periodicity.ANUAL,
                ServiceType.PREVENTIVO, estado, UUID.randomUUID(), true);
    }

    private static ReferenceStandard unPatron(boolean activo) {
        return ReferenceStandard.rehydrate(UUID.randomUUID(), "Simulador", null, "S-1", "CAL-1",
                LocalDate.now().plusYears(1), ReferenceStandardSheet.VACIA, activo);
    }

    /** Una orden con un equipo del tipo, que hoy lleva estos patrones para él. */
    private void unaOrdenConElTipo(ExecutionState estado, UUID... lleva) {
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrden(estado));
        lenient().when(workOrderReportsPort.activeReports(ORDEN)).thenReturn(
                List.of(new WorkOrderReportsPort.ActiveReport(UUID.randomUUID(), EQUIPO, "ABC123", false)));
        lenient().when(clientEquipmentServicePort.equipmentTypesOf(List.of(EQUIPO))).thenReturn(Map.of(EQUIPO, TIPO));
        lenient().when(workOrderStandardsPersistencePort.find(ORDEN, TIPO))
                .thenReturn(WorkOrderStandards.rehydrate(ORDEN, TIPO, List.of(lleva)));
        lenient().when(workOrderStandardsPersistencePort.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void elCatalogoAdmite(ReferenceStandard... patrones) {
        for (ReferenceStandard patron : patrones) {
            lenient().when(referenceStandardServicePort.findById(patron.getId())).thenReturn(patron);
        }
        when(workOrderStandardsCatalog.admitted(TIPO)).thenReturn(
                java.util.Arrays.stream(patrones).map(ReferenceStandard::getId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @DisplayName("fijarlos los guarda, lo anuncia y lleva a los reportes lo que se puso y lo que se quito")
    void fijarLlegaALosReportes() {
        ReferenceStandard viejo = unPatron(true);
        ReferenceStandard nuevo = unPatron(true);
        ReferenceStandard sigue = unPatron(true);
        unaOrdenConElTipo(ExecutionState.EN_EJECUCION, viejo.getId(), sigue.getId());
        elCatalogoAdmite(viejo, nuevo, sigue);

        WorkOrderStandards fijados = service.assign(
                new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of(sigue.getId(), nuevo.getId())));

        assertThat(fijados.getPatrones()).containsExactlyInAnyOrder(sigue.getId(), nuevo.getId());
        verify(workOrderStandardsPersistencePort).save(any());
        verify(eventDispatcherPort).dispatch(any());
        verify(workOrderReportsPort).applyStandards(ORDEN, TIPO, Set.of(nuevo.getId()), Set.of(viejo.getId()));
    }

    @Test
    @DisplayName("fijar lo mismo no guarda, no anuncia y no toca los reportes")
    void loMismo() {
        ReferenceStandard patron = unPatron(true);
        unaOrdenConElTipo(ExecutionState.CREADA, patron.getId());
        elCatalogoAdmite(patron);

        service.assign(new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of(patron.getId())));

        verify(workOrderStandardsPersistencePort, never()).save(any());
        verify(workOrderReportsPort, never()).applyStandards(any(), any(), any(), any());
    }

    @Test
    @DisplayName("un tipo sin equipos en la orden no lleva patrones")
    void tipoSinEquipos() {
        unaOrdenConElTipo(ExecutionState.CREADA);

        assertThatIllegalArgumentException().isThrownBy(() -> service.assign(
                        new AssignWorkOrderStandardsCommand(ORDEN, UUID.randomUUID(), List.of())))
                .withMessageContaining("no tiene equipos del tipo");
        verify(workOrderStandardsPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("un patron que el catalogo no admite en ninguna verificacion del tipo no entra")
    void fueraDelCatalogo() {
        ReferenceStandard admitido = unPatron(true);
        ReferenceStandard ajeno = unPatron(true);
        unaOrdenConElTipo(ExecutionState.CREADA);
        elCatalogoAdmite(admitido);
        when(referenceStandardServicePort.findById(ajeno.getId())).thenReturn(ajeno);

        assertThatIllegalArgumentException().isThrownBy(() -> service.assign(
                        new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of(ajeno.getId()))))
                .withMessageContaining("no es de los que el catalogo admite");
        verify(workOrderStandardsPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("un patron retirado no se anade, pero el que ya llevaba se puede quedar")
    void retirado() {
        ReferenceStandard yaEstaba = unPatron(false);
        ReferenceStandard nuevo = unPatron(false);
        unaOrdenConElTipo(ExecutionState.CREADA, yaEstaba.getId());
        elCatalogoAdmite(yaEstaba, nuevo);

        assertThatIllegalArgumentException().isThrownBy(() -> service.assign(
                        new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of(yaEstaba.getId(), nuevo.getId()))))
                .withMessageContaining("retirado");

        assertThat(service.assign(new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of(yaEstaba.getId())))
                .getPatrones()).containsExactly(yaEstaba.getId());
    }

    @Test
    @DisplayName("un patron que no existe se dice con su propia excepcion")
    void noExiste() {
        unaOrdenConElTipo(ExecutionState.CREADA);
        UUID inventado = UUID.randomUUID();
        when(workOrderStandardsCatalog.admitted(TIPO)).thenReturn(Set.of());
        when(referenceStandardServicePort.findById(inventado)).thenThrow(new ReferenceStandardNotFoundException(inventado));

        assertThatThrownBy(() -> service.assign(new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of(inventado))))
                .isInstanceOf(ReferenceStandardNotFoundException.class);
    }

    @Test
    @DisplayName("en una orden ejecutada no se cambian")
    void ejecutada() {
        unaOrdenConElTipo(ExecutionState.EJECUTADA);

        assertThatIllegalStateException().isThrownBy(() -> service.assign(
                        new AssignWorkOrderStandardsCommand(ORDEN, TIPO, List.of())))
                .withMessageContaining("ya ejecutada");
        verify(workOrderStandardsPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("consultarlos da uno por tipo con equipos en la orden, vacio si no lleva ninguno")
    void consultar() {
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrden(ExecutionState.CREADA));
        UUID otroEquipo = UUID.randomUUID();
        UUID otroTipo = UUID.randomUUID();
        UUID patron = UUID.randomUUID();
        when(workOrderReportsPort.activeReports(ORDEN)).thenReturn(List.of(
                new WorkOrderReportsPort.ActiveReport(UUID.randomUUID(), EQUIPO, "ABC123", false),
                new WorkOrderReportsPort.ActiveReport(UUID.randomUUID(), otroEquipo, "DEF456", false)));
        when(clientEquipmentServicePort.equipmentTypesOf(List.of(EQUIPO, otroEquipo)))
                .thenReturn(Map.of(EQUIPO, TIPO, otroEquipo, otroTipo));
        when(workOrderStandardsPersistencePort.findByWorkOrder(ORDEN))
                .thenReturn(List.of(WorkOrderStandards.rehydrate(ORDEN, TIPO, List.of(patron))));

        assertThat(service.standardsOf(ORDEN, ReadScope.unrestricted()))
                .extracting(WorkOrderStandards::getIdTipoEquipo, WorkOrderStandards::getPatrones)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(TIPO, Set.of(patron)),
                        org.assertj.core.groups.Tuple.tuple(otroTipo, Set.of()));
    }
}
