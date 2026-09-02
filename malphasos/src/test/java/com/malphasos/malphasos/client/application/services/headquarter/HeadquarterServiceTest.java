package com.malphasos.malphasos.client.application.services.headquarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.DeactivateHeadquarterCommand;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeadquarterServiceTest {

    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID CIUDAD = UUID.randomUUID();
    private static final Address DIRECCION = new Address("10", "20", "30-40");

    @Mock private HeadquarterPersistencePort headquarterPort;
    @Mock private ClientPersistencePort clientPort;
    @Mock private CityServicePort cityServicePort;
    @Mock private EventDispatcherPort dispatcher;

    private HeadquarterService service() {
        return new HeadquarterService(headquarterPort, clientPort, cityServicePort, dispatcher);
    }

    private void elClienteExiste() {
        when(clientPort.findById(CLIENTE)).thenReturn(Optional.of(
                Client.rehydrate(CLIENTE, "900123456", IdentificationType.NIT_JURIDICO,
                        "Hospital", null, true, List.of(), List.of(), Set.of())));
    }

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> despachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Test
    @DisplayName("abrir una sede comprueba cliente y ciudad, y publica el hecho")
    void abrirSede() {
        elClienteExiste();
        when(headquarterPort.save(any(Headquarter.class))).thenAnswer(i -> i.getArgument(0));

        service().create(new CreateHeadquarterCommand("Sede Norte", DIRECCION, CLIENTE, CIUDAD));

        verify(cityServicePort).findById(CIUDAD);
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("headquarter.created");
    }

    @Test
    @DisplayName("si el cliente no existe se dice eso, no un conflicto de datos")
    void clienteInexistente() {
        when(clientPort.findById(CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(
                        new CreateHeadquarterCommand("Sede", DIRECCION, CLIENTE, CIUDAD)))
                .isInstanceOf(ClientNotFoundException.class);

        verify(headquarterPort, never()).save(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("si la ciudad no existe tampoco se abre la sede")
    void ciudadInexistente() {
        elClienteExiste();
        when(cityServicePort.findById(CIUDAD)).thenThrow(new CityNotFoundException(CIUDAD));

        assertThatThrownBy(() -> service().create(
                        new CreateHeadquarterCommand("Sede", DIRECCION, CLIENTE, CIUDAD)))
                .isInstanceOf(CityNotFoundException.class);

        verify(headquarterPort, never()).save(any());
    }

    @Test
    @DisplayName("listar por un cliente que no existe falla en vez de devolver vacio")
    void listarPorClienteInexistente() {
        when(clientPort.findById(CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findByClient(CLIENTE))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    @DisplayName("cerrar deja la sede inactiva y publica el hecho")
    void cerrar() {
        UUID id = UUID.randomUUID();
        when(headquarterPort.findById(id)).thenReturn(Optional.of(
                Headquarter.rehydrate(id, "Sede Norte", DIRECCION, CLIENTE, CIUDAD, true)));
        when(headquarterPort.save(any(Headquarter.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(new DeactivateHeadquarterCommand(id));

        ArgumentCaptor<Headquarter> guardada = ArgumentCaptor.forClass(Headquarter.class);
        verify(headquarterPort).save(guardada.capture());

        assertThat(guardada.getValue().isEstadoActivo()).isFalse();
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("headquarter.deactivated");
    }

    @Test
    @DisplayName("una sede que no existe falla antes de tocar nada")
    void sedeInexistente() {
        UUID id = UUID.randomUUID();
        when(headquarterPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(new DeactivateHeadquarterCommand(id)))
                .isInstanceOf(HeadquarterNotFoundException.class);

        verifyNoInteractions(dispatcher);
    }
}
