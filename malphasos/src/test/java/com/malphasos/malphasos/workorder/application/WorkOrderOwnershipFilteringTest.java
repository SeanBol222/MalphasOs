package com.malphasos.malphasos.workorder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.WorkOrderService;
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
 * Las seis lecturas de órdenes acotan por dueño, y tres de ellas sin comprobar nada.
 *
 * <p>Ese es el hallazgo que esta prueba fija: {@code ?idCliente=}, {@code ?idSede=} y
 * {@code ?idEquipoCliente=} <b>delegan</b> —le pasan el alcance a quien sabe de quién es ese
 * recurso, y la comprobación de existencia y la de pertenencia pasan a ser la misma llamada—. Pasarle
 * {@code unrestricted()} en su lugar compilaría igual y no filtraría nada, de modo que cada una lleva
 * una verificación de que el alcance se le pasa de verdad.
 *
 * <p>{@code ?idIngeniero=} es la única que no puede delegar: un ingeniero no pertenece a ningún
 * cliente, así que no hay a quién preguntar por él y lo que se acota es el resultado.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderOwnershipFilteringTest {

    private static final UUID MIO = UUID.randomUUID();
    private static final UUID AJENO = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID INGENIERO = UUID.randomUUID();

    private static final ReadScope SOLO_MIO = ReadScope.ofClients(Set.of(MIO));

    @Mock private WorkOrderPersistencePort workOrderPersistencePort;
    @Mock private ClientServicePort clientServicePort;
    @Mock private HeadquarterServicePort headquarterServicePort;
    @Mock private ServiceAreaServicePort serviceAreaServicePort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private PersonCommunicationPort personCommunicationPort;
    @Mock private EventDispatcherPort eventDispatcherPort;

    @InjectMocks private WorkOrderService service;

    private WorkOrder unaOrden(UUID idCliente) {
        return WorkOrder.schedule(
                idCliente, SEDE, LocalDate.now().plusDays(1), Periodicity.ANUAL, ServiceType.PREVENTIVO);
    }

    @Test
    @DisplayName("el listado completo trae solo las ordenes de los clientes del alcance")
    void listadoAcotado() {
        when(workOrderPersistencePort.findByClientIn(Set.of(MIO))).thenReturn(List.of(unaOrden(MIO)));

        assertThat(service.findAll(SOLO_MIO)).hasSize(1);
        verify(workOrderPersistencePort, never()).findAll();
    }

    @Test
    @DisplayName("sin restriccion el listado completo no pasa por la consulta filtrada")
    void listadoLibre() {
        when(workOrderPersistencePort.findAll()).thenReturn(List.of(unaOrden(MIO), unaOrden(AJENO)));

        assertThat(service.findAll(ReadScope.unrestricted())).hasSize(2);
        verify(workOrderPersistencePort, never()).findByClientIn(any());
    }

    @Test
    @DisplayName("una orden de otro cliente no existe para quien pregunta")
    void ordenDeOtroCliente() {
        WorkOrder ajena = unaOrden(AJENO);
        when(workOrderPersistencePort.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.findById(ajena.getId(), SOLO_MIO))
                .isInstanceOf(WorkOrderNotFoundException.class);
    }

    @Test
    @DisplayName("la propia orden se devuelve, y el dueno no cuesta ninguna consulta")
    void ordenPropia() {
        WorkOrder mia = unaOrden(MIO);
        when(workOrderPersistencePort.findById(mia.getId())).thenReturn(Optional.of(mia));

        assertThat(service.findById(mia.getId(), SOLO_MIO).getId()).isEqualTo(mia.getId());

        // Una orden guarda su cliente: es el unico de los cuatro modulos acotados donde saber de
        // quien es algo sale gratis.
        verify(clientServicePort, never()).findById(any(), any());
    }

    @Test
    @DisplayName("filtrar por un cliente ajeno lo rechaza el modulo de clientes, con el alcance")
    void porClienteAjeno() {
        when(clientServicePort.findById(AJENO, SOLO_MIO)).thenThrow(new ClientNotFoundException(AJENO));

        assertThatThrownBy(() -> service.findByClient(AJENO, SOLO_MIO))
                .isInstanceOf(ClientNotFoundException.class);

        verify(workOrderPersistencePort, never()).findByClient(any());
    }

    @Test
    @DisplayName("filtrar por el cliente propio devuelve sus ordenes, y se le pasa el alcance")
    void porClientePropio() {
        when(workOrderPersistencePort.findByClient(MIO)).thenReturn(List.of(unaOrden(MIO)));

        assertThat(service.findByClient(MIO, SOLO_MIO)).hasSize(1);

        // Lo que se fija: que el alcance viaje. Con unrestricted() esto compilaria y no filtraria.
        verify(clientServicePort).findById(MIO, SOLO_MIO);
    }

    @Test
    @DisplayName("filtrar por una sede ajena lo rechaza el modulo de clientes, con el alcance")
    void porSedeAjena() {
        when(headquarterServicePort.findById(SEDE, SOLO_MIO))
                .thenThrow(new HeadquarterNotFoundException(SEDE));

        assertThatThrownBy(() -> service.findByHeadquarter(SEDE, SOLO_MIO))
                .isInstanceOf(HeadquarterNotFoundException.class);

        verify(workOrderPersistencePort, never()).findByHeadquarter(any());
    }

    @Test
    @DisplayName("filtrar por un equipo ajeno lo rechaza el modulo de equipos, con el alcance")
    void porEquipoAjeno() {
        when(clientEquipmentServicePort.findById(EQUIPO, SOLO_MIO))
                .thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> service.findByEquipment(EQUIPO, SOLO_MIO))
                .isInstanceOf(ClientEquipmentNotFoundException.class);

        verify(workOrderPersistencePort, never()).findByEquipment(any());
    }

    @Test
    @DisplayName("por ingeniero se acota el resultado: sus ordenes en MIS clientes, no su agenda")
    void porIngenieroSeAcotaElResultado() {
        when(workOrderPersistencePort.findByEngineerAndClientIn(INGENIERO, Set.of(MIO)))
                .thenReturn(List.of(unaOrden(MIO)));

        assertThat(service.findByEngineer(INGENIERO, SOLO_MIO)).hasSize(1);

        // La agenda completa del ingeniero incluye las ordenes de otros clientes, y no se pide.
        verify(workOrderPersistencePort, never()).findByEngineer(any());
    }

    @Test
    @DisplayName("sin restriccion por ingeniero se devuelve su agenda entera")
    void porIngenieroLibre() {
        when(workOrderPersistencePort.findByEngineer(INGENIERO))
                .thenReturn(List.of(unaOrden(MIO), unaOrden(AJENO)));

        assertThat(service.findByEngineer(INGENIERO, ReadScope.unrestricted())).hasSize(2);
        verify(workOrderPersistencePort, never()).findByEngineerAndClientIn(any(), any());
    }
}
