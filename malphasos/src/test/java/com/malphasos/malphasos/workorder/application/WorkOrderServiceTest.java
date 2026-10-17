package com.malphasos.malphasos.workorder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.application.services.workOrder.WorkOrderService;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AssignWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ScheduleWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderHasOpenReportsException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las siete reglas que ni el esquema ni el agregado pueden defender, porque todas exigen preguntar
 * a otro módulo.
 *
 * <p>Las referencias se comprueban <b>activas</b> y no solo existentes: una clave foránea confirma
 * que la fila está, y con borrado lógico eso dejó de significar que siga en uso.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID INGENIERO = UUID.randomUUID();
    private static final LocalDate MANANA = LocalDate.now().plusDays(1);

    @Mock private WorkOrderPersistencePort workOrderPersistencePort;
    @Mock private ClientServicePort clientServicePort;
    @Mock private HeadquarterServicePort headquarterServicePort;
    @Mock private ServiceAreaServicePort serviceAreaServicePort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private PersonCommunicationPort personCommunicationPort;
    @Mock private EventDispatcherPort eventDispatcherPort;
    @Mock private WorkOrderReportsPort workOrderReportsPort;

    @InjectMocks private WorkOrderService service;

    private WorkOrder orden;

    @BeforeEach
    void prepararLaOrden() {
        orden = WorkOrder.schedule(CLIENTE, SEDE, MANANA, Periodicity.ANUAL, ServiceType.PREVENTIVO);
        orden.pullEvents();
    }

    private static Client unCliente(boolean activo) {
        return Client.rehydrate(CLIENTE, "900123456", IdentificationType.NIT_JURIDICO, "Hospital", "CLI",
                UUID.randomUUID(), activo, List.of(), List.of(), Set.of());
    }

    private static Headquarter unaSede(UUID idCliente, boolean activa) {
        return Headquarter.rehydrate(SEDE, "Sede norte", new Address("10", "20", "30-40"),
                idCliente, UUID.randomUUID(), activa);
    }

    private static ServiceArea unArea(boolean activa) {
        return unArea(activa, SEDE);
    }

    private static ServiceArea unArea(boolean activa, UUID idSede) {
        return ServiceArea.rehydrate(AREA, "Urgencias", idSede, activa);
    }

    private static ClientEquipment unaUnidad(UUID area, boolean activa) {
        return ClientEquipment.rehydrate(EQUIPO, "SN-1", UUID.randomUUID(), area, null, null, null, null, null, null,
                activa);
    }

    private static PersonCommunicationResponse unaPersona(PersonType tipo, PersonType segundo,
            boolean activa) {
        return new PersonCommunicationResponse(INGENIERO, "1010101010", "Grace", null, "Hopper",
                null, tipo, segundo, activa, List.of(), List.of());
    }

    /** El almacén devuelve lo que le dan: aquí no se prueba la persistencia. */
    private void elAlmacenGuardaYDevuelve() {
        when(workOrderPersistencePort.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void laOrdenExiste() {
        when(workOrderPersistencePort.findById(orden.getId())).thenReturn(Optional.of(orden));
    }

    @Nested
    @DisplayName("Al programar")
    class AlProgramar {

        private ScheduleWorkOrderCommand elComando() {
            return new ScheduleWorkOrderCommand(
                    CLIENTE, SEDE, MANANA, Periodicity.ANUAL, ServiceType.PREVENTIVO, false);
        }

        @Test
        @DisplayName("con cliente y sede activos, la orden se programa")
        void seProgramaCuandoTodoEstaEnOrden() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted())).thenReturn(unaSede(CLIENTE, true));
            elAlmacenGuardaYDevuelve();

            WorkOrder creada = service.schedule(elComando());

            assertThat(creada.getIdCliente()).isEqualTo(CLIENTE);
            assertThat(creada.getIdSede()).isEqualTo(SEDE);
            verify(eventDispatcherPort).dispatch(any());
        }

        @Test
        @DisplayName("un correctivo en garantia llega asi al agregado")
        void enGarantia() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted())).thenReturn(unaSede(CLIENTE, true));
            elAlmacenGuardaYDevuelve();

            WorkOrder creada = service.schedule(new ScheduleWorkOrderCommand(
                    CLIENTE, SEDE, MANANA, Periodicity.ANUAL, ServiceType.CORRECTIVO, true));

            assertThat(creada.isGarantia()).isTrue();
        }

        @Test
        @DisplayName("si el numero ya lo tiene otra orden, se elige otro identificador y se guarda ese")
        void numeroTomado() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted())).thenReturn(unaSede(CLIENTE, true));
            when(workOrderPersistencePort.numberTaken(any())).thenReturn(true, false);
            elAlmacenGuardaYDevuelve();

            WorkOrder creada = service.schedule(elComando());

            org.mockito.ArgumentCaptor<String> numeros = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(workOrderPersistencePort, org.mockito.Mockito.times(2)).numberTaken(numeros.capture());
            assertThat(numeros.getAllValues().get(1)).isEqualTo(creada.numero());
            assertThat(numeros.getAllValues().get(0)).isNotEqualTo(creada.numero());
        }

        @Test
        @DisplayName("sin numero libre tras los intentos, no se guarda nada y lo dice")
        void sinNumeroLibre() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted())).thenReturn(unaSede(CLIENTE, true));
            when(workOrderPersistencePort.numberTaken(any())).thenReturn(true);

            org.assertj.core.api.Assertions.assertThatIllegalStateException()
                    .isThrownBy(() -> service.schedule(elComando()))
                    .withMessageContaining("numero de orden libre");

            verify(workOrderPersistencePort, never()).save(any());
            verify(eventDispatcherPort, never()).dispatch(any());
        }

        @Test
        @DisplayName("un cliente retirado no recibe mantenimientos nuevos")
        void clienteRetirado() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(false));

            assertThatIllegalArgumentException().isThrownBy(() -> service.schedule(elComando()))
                    .withMessageContaining("retirado");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una sede cerrada tampoco")
        void sedeCerrada() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted())).thenReturn(unaSede(CLIENTE, false));

            assertThatIllegalArgumentException().isThrownBy(() -> service.schedule(elComando()))
                    .withMessageContaining("cerrada");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una sede de otro cliente se rechaza aqui, no como conflicto de integridad")
        void sedeDeOtroCliente() {
            // La clave foranea compuesta lo impediria igualmente, pero el llamante recibiria un
            // conflicto generico que no le dice cual de las dos referencias falla.
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted()))
                    .thenReturn(unaSede(UUID.randomUUID(), true));

            assertThatIllegalArgumentException().isThrownBy(() -> service.schedule(elComando()))
                    .withMessageContaining("no es del cliente");

            verify(workOrderPersistencePort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Al anadir un equipo")
    class AlAnadirUnEquipo {

        private AddEquipmentToWorkOrderCommand elComando() {
            return new AddEquipmentToWorkOrderCommand(orden.getId(), EQUIPO);
        }

        @Test
        @DisplayName("el area que se congela la averigua el servicio, no la declara el llamante")
        void elAreaSaleDelEquipoYNoDelComando() {
            // Es la regla mas importante de esta capa: el comando no tiene sitio para un area, de
            // modo que nadie puede declarar una donde el equipo no esta.
            laOrdenExiste();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(unaUnidad(AREA, true));
            when(serviceAreaServicePort.findById(AREA, ReadScope.unrestricted())).thenReturn(unArea(true));
            when(serviceAreaServicePort.findOwningClient(AREA)).thenReturn(CLIENTE);
            elAlmacenGuardaYDevuelve();

            WorkOrder conEquipo = service.addEquipment(elComando());

            assertThat(conEquipo.getEquipos()).singleElement()
                    .extracting(SelectedEquipment::getIdEquipoCliente,
                            SelectedEquipment::getIdAreaServicio)
                    .containsExactly(EQUIPO, AREA);
        }

        @Test
        @DisplayName("una unidad dada de baja no entra en una orden")
        void unidadDadaDeBaja() {
            laOrdenExiste();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(unaUnidad(AREA, false));

            assertThatIllegalArgumentException().isThrownBy(() -> service.addEquipment(elComando()))
                    .withMessageContaining("dada de baja");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un equipo en un area cerrada tampoco: seria programar donde no se opera")
        void areaCerrada() {
            laOrdenExiste();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(unaUnidad(AREA, true));
            when(serviceAreaServicePort.findById(AREA, ReadScope.unrestricted())).thenReturn(unArea(false));

            assertThatIllegalArgumentException().isThrownBy(() -> service.addEquipment(elComando()))
                    .withMessageContaining("cerrada");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un equipo de otro cliente no entra en la orden")
        void equipoDeOtroCliente() {
            laOrdenExiste();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(unaUnidad(AREA, true));
            when(serviceAreaServicePort.findById(AREA, ReadScope.unrestricted())).thenReturn(unArea(true));
            when(serviceAreaServicePort.findOwningClient(AREA)).thenReturn(UUID.randomUUID());

            assertThatIllegalArgumentException().isThrownBy(() -> service.addEquipment(elComando()))
                    .withMessageContaining("es del cliente");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un equipo del mismo cliente pero de otra sede tampoco entra")
        void equipoDeOtraSedeDelMismoCliente() {
            // El caso que se colaba: pasa las tres comprobaciones que habia -unidad activa, area
            // abierta, mismo cliente- porque el cliente si coincide. Una orden se presta en un
            // sitio, y un equipo de otra sede no se va a intervenir ese dia.
            laOrdenExiste();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(unaUnidad(AREA, true));
            when(serviceAreaServicePort.findById(AREA, ReadScope.unrestricted()))
                    .thenReturn(unArea(true, UUID.randomUUID()));
            when(serviceAreaServicePort.findOwningClient(AREA)).thenReturn(CLIENTE);

            assertThatIllegalArgumentException().isThrownBy(() -> service.addEquipment(elComando()))
                    .withMessageContaining("se presta en la sede");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("la negativa por cliente sigue siendo alcanzable y no la tapa la de sede")
        void elClienteSeCompruebaAntesQueLaSede() {
            // Un equipo de otro cliente esta necesariamente en otra sede, asi que las dos negativas
            // serian ciertas. Se comprueba el cliente primero porque es la mas informativa de las
            // dos; si alguien invierte el orden, esta prueba lo dice.
            laOrdenExiste();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(unaUnidad(AREA, true));
            when(serviceAreaServicePort.findById(AREA, ReadScope.unrestricted()))
                    .thenReturn(unArea(true, UUID.randomUUID()));
            when(serviceAreaServicePort.findOwningClient(AREA)).thenReturn(UUID.randomUUID());

            assertThatIllegalArgumentException().isThrownBy(() -> service.addEquipment(elComando()))
                    .withMessageContaining("es del cliente")
                    .withMessageNotContaining("se presta en la sede");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("si la orden no existe no se consulta a nadie mas")
        void ordenInexistente() {
            when(workOrderPersistencePort.findById(orden.getId())).thenReturn(Optional.empty());

            Assertions.assertThatExceptionOfType(WorkOrderNotFoundException.class)
                    .isThrownBy(() -> service.addEquipment(elComando()));

            verify(clientEquipmentServicePort, never()).findById(any(), any());
            verify(workOrderPersistencePort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Al asignar")
    class AlAsignar {

        private AssignWorkOrderCommand elComando() {
            return new AssignWorkOrderCommand(orden.getId(), INGENIERO);
        }

        @Test
        @DisplayName("un ingeniero activo se hace cargo")
        void ingenieroActivo() {
            laOrdenExiste();
            when(personCommunicationPort.findById(INGENIERO))
                    .thenReturn(unaPersona(PersonType.ENGINEER, null, true));
            elAlmacenGuardaYDevuelve();

            assertThat(service.assign(elComando()).getIdIngeniero()).isEqualTo(INGENIERO);
        }

        @Test
        @DisplayName("quien es ingeniero por su segundo tipo tambien puede")
        void ingenieroPorSegundoTipo() {
            // Una persona puede tener dos tipos: quien es encargado y ademas ingeniero sigue
            // pudiendo ejecutar un mantenimiento. Excluirlo seria un criterio administrativo.
            laOrdenExiste();
            when(personCommunicationPort.findById(INGENIERO))
                    .thenReturn(unaPersona(PersonType.MANAGER, PersonType.ENGINEER, true));
            elAlmacenGuardaYDevuelve();

            assertThat(service.assign(elComando()).getIdIngeniero()).isEqualTo(INGENIERO);
        }

        @Test
        @DisplayName("quien no es ingeniero no ejecuta mantenimientos")
        void noEsIngeniero() {
            laOrdenExiste();
            when(personCommunicationPort.findById(INGENIERO))
                    .thenReturn(unaPersona(PersonType.MANAGER, null, true));

            assertThatIllegalArgumentException().isThrownBy(() -> service.assign(elComando()))
                    .withMessageContaining("no lo es");

            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una persona retirada no recibe ordenes")
        void personaRetirada() {
            laOrdenExiste();
            when(personCommunicationPort.findById(INGENIERO))
                    .thenReturn(unaPersona(PersonType.ENGINEER, null, false));

            assertThatIllegalArgumentException().isThrownBy(() -> service.assign(elComando()))
                    .withMessageContaining("retirada");

            verify(workOrderPersistencePort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Los eventos")
    class LosEventos {

        @Test
        @DisplayName("se publican los del agregado recibido, no los del devuelto por el almacen")
        void sePublicanLosDelRecibido() {
            // El agregado que devuelve el almacen se rehidrata y por eso viene sin eventos. Si se
            // publicaran los suyos no se publicaria ninguno, y nadie se enteraria de nada.
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(headquarterServicePort.findById(SEDE, ReadScope.unrestricted())).thenReturn(unaSede(CLIENTE, true));
            when(workOrderPersistencePort.save(any())).thenAnswer(invocacion -> {
                WorkOrder recibida = invocacion.getArgument(0);
                return WorkOrder.rehydrate(recibida.getId(), CLIENTE, SEDE, MANANA,
                        Periodicity.ANUAL, ServiceType.PREVENTIVO,
                        recibida.getEstadoEjecucion(), null, Set.of(), true);
            });

            service.schedule(new ScheduleWorkOrderCommand(
                    CLIENTE, SEDE, MANANA, Periodicity.ANUAL, ServiceType.PREVENTIVO, false));

            verify(eventDispatcherPort).dispatch(any());
        }

        @Test
        @DisplayName("una operacion que no cambia nada no publica nada")
        void loQueNoCambiaNoSePublica() {
            laOrdenExiste();
            elAlmacenGuardaYDevuelve();

            // Retirar un equipo que no esta en el alcance.
            service.removeEquipment(
                    new com.malphasos.malphasos.workorder.application.services.workOrder.commands
                            .RemoveEquipmentFromWorkOrderCommand(orden.getId(), UUID.randomUUID()));

            verify(eventDispatcherPort, never()).dispatch(any());
        }
    }

    /**
     * Ejecutar exige todos los reportes cerrados (2026-10-14). La regla vive en el servicio porque los
     * reportes son de otro módulo, y se pregunta por un puerto que ese módulo implementa.
     */
    @Nested
    @DisplayName("Al ejecutar")
    class AlEjecutar {

        private static final UUID OTRO_EQUIPO = UUID.randomUUID();

        private WorkOrder enEjecucionCon(UUID... equipos) {
            Set<SelectedEquipment> alcance = new java.util.HashSet<>();
            for (UUID equipo : equipos) {
                alcance.add(SelectedEquipment.of(equipo, AREA));
            }
            WorkOrder enEjecucion = WorkOrder.rehydrate(UUID.randomUUID(), CLIENTE, SEDE, MANANA,
                    Periodicity.ANUAL, ServiceType.PREVENTIVO, ExecutionState.EN_EJECUCION, INGENIERO,
                    alcance, true);
            when(workOrderPersistencePort.findById(enEjecucion.getId())).thenReturn(Optional.of(enEjecucion));
            return enEjecucion;
        }

        @Test
        @DisplayName("con todos los reportes cerrados se ejecuta")
        void todosCerrados() {
            WorkOrder enEjecucion = enEjecucionCon(EQUIPO, OTRO_EQUIPO);
            when(workOrderReportsPort.equipmentWithFinishedReport(enEjecucion.getId()))
                    .thenReturn(Set.of(EQUIPO, OTRO_EQUIPO));
            elAlmacenGuardaYDevuelve();

            assertThat(service.execute(new ExecuteWorkOrderCommand(enEjecucion.getId())).getEstadoEjecucion())
                    .isEqualTo(ExecutionState.EJECUTADA);
        }

        @Test
        @DisplayName("con un equipo sin reporte cerrado no se ejecuta, y dice cuántos faltan")
        void faltaUno() {
            WorkOrder enEjecucion = enEjecucionCon(EQUIPO, OTRO_EQUIPO);
            when(workOrderReportsPort.equipmentWithFinishedReport(enEjecucion.getId())).thenReturn(Set.of(EQUIPO));

            assertThatThrownBy(() -> service.execute(new ExecuteWorkOrderCommand(enEjecucion.getId())))
                    .isInstanceOf(WorkOrderHasOpenReportsException.class)
                    .hasMessageContaining("1 equipo sin reporte cerrado")
                    .satisfies(e -> assertThat(((WorkOrderHasOpenReportsException) e).getPendientes()).isEqualTo(1));
            verify(workOrderPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un reporte cerrado de un equipo que ya no está en la orden no cuenta por otro")
        void cerradoAjeno() {
            WorkOrder enEjecucion = enEjecucionCon(EQUIPO);
            when(workOrderReportsPort.equipmentWithFinishedReport(enEjecucion.getId()))
                    .thenReturn(Set.of(OTRO_EQUIPO));

            assertThatThrownBy(() -> service.execute(new ExecuteWorkOrderCommand(enEjecucion.getId())))
                    .isInstanceOf(WorkOrderHasOpenReportsException.class);
        }

        @Test
        @DisplayName("una orden sin empezar responde con su estado, sin preguntar por los reportes")
        void sinEmpezar() {
            laOrdenExiste();
            // Si los reportes se miraran primero, esto diría «faltan reportes» de una orden que ni
            // siquiera puede tenerlos todavía. Se estubaría el camino equivocado sin el lenient.
            lenient().when(workOrderReportsPort.equipmentWithFinishedReport(any())).thenReturn(Set.of());

            assertThatThrownBy(() -> service.execute(new ExecuteWorkOrderCommand(orden.getId())))
                    .isInstanceOf(IllegalStateException.class);
            verify(workOrderReportsPort, never()).equipmentWithFinishedReport(any());
        }
    }

    @Nested
    @DisplayName("Las consultas comprueban que la referencia existe")
    class LasConsultas {

        @Test
        @DisplayName("buscar por cliente valida el cliente antes de mirar el almacen")
        void porCliente() {
            when(clientServicePort.findById(CLIENTE, ReadScope.unrestricted())).thenReturn(unCliente(true));
            when(workOrderPersistencePort.findByClient(CLIENTE)).thenReturn(List.of());

            assertThat(service.findByClient(CLIENTE, ReadScope.unrestricted())).isEmpty();
            verify(clientServicePort).findById(CLIENTE, ReadScope.unrestricted());
        }

        @Test
        @DisplayName("una orden inexistente se anuncia como tal")
        void ordenInexistente() {
            UUID desconocida = UUID.randomUUID();
            when(workOrderPersistencePort.findById(desconocida)).thenReturn(Optional.empty());

            Assertions.assertThatExceptionOfType(WorkOrderNotFoundException.class)
                    .isThrownBy(() -> service.findById(desconocida, ReadScope.unrestricted()));
        }
    }
}
