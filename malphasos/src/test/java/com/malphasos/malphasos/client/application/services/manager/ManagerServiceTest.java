package com.malphasos.malphasos.client.application.services.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ManagerPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.application.services.manager.commands.AssignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.DeactivateManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.ReassignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RegisterManagerCommand;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ManagerNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    private static final UUID SEDE = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();
    private static final UUID PERSONA = UUID.randomUUID();

    @Mock private ManagerPersistencePort managerPort;
    @Mock private HeadquarterPersistencePort headquarterPort;
    @Mock private ServiceAreaPersistencePort areaPort;
    @Mock private PersonCommunicationPort personPort;
    @Mock private EventDispatcherPort dispatcher;

    private ManagerService service() {
        return new ManagerService(managerPort, headquarterPort, areaPort, personPort, dispatcher);
    }

    private void laSedeEsta(boolean activa) {
        when(headquarterPort.findById(SEDE)).thenReturn(Optional.of(
                Headquarter.rehydrate(SEDE, "Sede Norte", new Address("10", "20", "30-40"),
                        UUID.randomUUID(), UUID.randomUUID(), activa)));
    }

    private PersonCommunicationRequest datosDePersona() {
        return PersonCommunicationRequest.builder()
                .cedula("1010101010")
                .primerNombre("Grace")
                .primerApellido("Hopper")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> despachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Test
    @DisplayName("registrar crea la persona y usa su identificador como el del encargado")
    void registrar() {
        laSedeEsta(true);
        when(personPort.save(any(PersonCommunicationRequest.class))).thenReturn(PERSONA);
        when(managerPort.save(any(Manager.class))).thenAnswer(i -> i.getArgument(0));

        Manager encargado = service().register(
                new RegisterManagerCommand(datosDePersona(), ManagerType.HEADQUARTER, SEDE));

        assertThat(encargado.getIdPersona()).isEqualTo(PERSONA);
        assertThat(encargado.getIdSede()).isEqualTo(SEDE);
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("manager.assigned");
    }

    @Test
    @DisplayName("el tipo de persona lo fija el servicio, no quien llama")
    void elTipoDePersonaLoFijaElServicio() {
        laSedeEsta(true);
        when(personPort.save(any(PersonCommunicationRequest.class))).thenReturn(PERSONA);
        when(managerPort.save(any(Manager.class))).thenAnswer(i -> i.getArgument(0));

        // La peticion llega con CEO_CLIENT y debe registrarse igualmente como MANAGER.
        PersonCommunicationRequest conOtroTipo = PersonCommunicationRequest.builder()
                .cedula("1010101010")
                .primerNombre("Grace")
                .primerApellido("Hopper")
                .tipoPersona(PersonType.CEO_CLIENT)
                .build();

        service().register(new RegisterManagerCommand(conOtroTipo, ManagerType.HEADQUARTER, SEDE));

        ArgumentCaptor<PersonCommunicationRequest> enviada =
                ArgumentCaptor.forClass(PersonCommunicationRequest.class);
        verify(personPort).save(enviada.capture());

        assertThat(enviada.getValue().tipoPersona()).isEqualTo(PersonType.MANAGER);
    }

    @Test
    @DisplayName("no se registra a nadie al frente de una sede cerrada, ni se crea la persona")
    void sedeCerrada() {
        laSedeEsta(false);

        assertThatThrownBy(() -> service().register(
                        new RegisterManagerCommand(datosDePersona(), ManagerType.HEADQUARTER, SEDE)))
                .isInstanceOf(HeadquarterNotFoundException.class);

        // Se comprueba el destino ANTES de crear la persona: si no, quedaria una persona huerfana.
        verifyNoInteractions(personPort);
        verify(managerPort, never()).save(any());
    }

    @Test
    @DisplayName("tampoco al frente de un area cerrada, que es la regla simetrica de la de arriba")
    void areaCerrada() {
        // ESTA FALTABA, y lo destapo una mutacion el 2026-10-04: el servicio tiene dos guardas
        // paralelas -- requireActiveHeadquarter y requireActiveServiceArea, identicas salvo el puerto
        // y la excepcion -- y solo la de la sede estaba probada. Quitar el `.filter(area ->
        // area.isEstadoActivo())` de la del area dejaba la bateria entera en verde: 851 pruebas.
        //
        // Dos guardas escritas a la vez y una sola prueba es la forma mas facil de que una de las dos
        // deje de funcionar sin que nada avise: la que tiene prueba protege a la otra de la revision,
        // no del defecto.
        when(areaPort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, false)));

        assertThatThrownBy(() -> service().register(
                        new RegisterManagerCommand(datosDePersona(), ManagerType.SERVICE_AREA, AREA)))
                .isInstanceOf(ServiceAreaNotFoundException.class);

        // Y por lo mismo que en la sede: el destino se comprueba ANTES de crear la persona.
        verifyNoInteractions(personPort);
        verify(managerPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar al frente de un area cerrada tampoco vale, no solo al registrar")
    void asignarAAreaCerrada() {
        // La guarda es la misma para las dos operaciones, y conviene fijarlo: `register` crea la
        // persona y `assign` usa una que ya existe, de modo que un arreglo que solo mirara una de las
        // dos entradas pasaria la prueba de arriba.
        when(areaPort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, false)));

        assertThatThrownBy(() -> service().assign(
                        new AssignManagerCommand(PERSONA, ManagerType.SERVICE_AREA, AREA)))
                .isInstanceOf(ServiceAreaNotFoundException.class);

        verify(managerPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar a alguien que ya existe no crea otra persona")
    void asignarPersonaExistente() {
        when(areaPort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, true)));
        when(managerPort.save(any(Manager.class))).thenAnswer(i -> i.getArgument(0));

        Manager encargado = service().assign(
                new AssignManagerCommand(PERSONA, ManagerType.SERVICE_AREA, AREA));

        // El original siempre creaba una persona nueva: un ingeniero de la empresa no podia figurar
        // ademas como encargado sin duplicarse.
        verify(personPort).findById(PERSONA);
        verify(personPort, never()).save(any());
        assertThat(encargado.getIdAreaServicio()).isEqualTo(AREA);
    }

    @Test
    @DisplayName("reasignar publica el traslado")
    void reasignar() {
        when(areaPort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, true)));
        when(managerPort.findByPerson(PERSONA)).thenReturn(Optional.of(
                Manager.rehydrate(PERSONA, ManagerType.HEADQUARTER, SEDE, true)));
        when(managerPort.save(any(Manager.class))).thenAnswer(i -> i.getArgument(0));

        service().reassign(new ReassignManagerCommand(PERSONA, ManagerType.SERVICE_AREA, AREA));

        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("manager.reassigned");
    }

    @Test
    @DisplayName("relevar deja el encargado inactivo y publica el hecho")
    void relevar() {
        when(managerPort.findByPerson(PERSONA)).thenReturn(Optional.of(
                Manager.rehydrate(PERSONA, ManagerType.HEADQUARTER, SEDE, true)));
        when(managerPort.save(any(Manager.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(new DeactivateManagerCommand(PERSONA));

        ArgumentCaptor<Manager> guardado = ArgumentCaptor.forClass(Manager.class);
        verify(managerPort).save(guardado.capture());

        assertThat(guardado.getValue().isEstadoActivo()).isFalse();
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("manager.deactivated");
    }

    @Test
    @DisplayName("operar sobre una persona que no es encargado falla antes de tocar nada")
    void noEsEncargado() {
        when(managerPort.findByPerson(PERSONA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(new DeactivateManagerCommand(PERSONA)))
                .isInstanceOf(ManagerNotFoundException.class);

        verifyNoInteractions(dispatcher);
    }
}
