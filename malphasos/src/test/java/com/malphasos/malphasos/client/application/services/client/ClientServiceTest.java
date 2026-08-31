package com.malphasos.malphasos.client.application.services.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.services.client.commands.AddClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AppointRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.CreateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.DeactivateClientCommand;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock private ClientPersistencePort persistencePort;
    @Mock private PersonCommunicationPort personCommunicationPort;
    @Mock private EventDispatcherPort dispatcher;

    private ClientService service() {
        return new ClientService(persistencePort, personCommunicationPort, dispatcher);
    }

    private Client unCliente(UUID id) {
        return Client.rehydrate(id, "900123456", IdentificationType.NIT_JURIDICO,
                "Hospital Central", null, true, List.of(), List.of(), Set.of());
    }

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> despachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Test
    @DisplayName("crear persiste el cliente y publica el hecho, en ese orden")
    void crear() {
        when(persistencePort.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));

        service().create(new CreateClientCommand(
                "900123456", IdentificationType.NIT_JURIDICO, "Hospital Central", null));

        InOrder orden = Mockito.inOrder(persistencePort, dispatcher);
        orden.verify(persistencePort).save(any(Client.class));
        orden.verify(dispatcher).dispatchAll(any());

        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("client.created");
    }

    @Test
    @DisplayName("si el almacen falla no se publica nada")
    void fallarAlPersistirNoPublica() {
        when(persistencePort.save(any(Client.class))).thenThrow(new RuntimeException("caida"));

        assertThatThrownBy(() -> service().create(new CreateClientCommand(
                        "900123456", IdentificationType.CC, "Hospital", null)))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("nombrar representante comprueba antes que la persona exista")
    void nombrarRepresentanteComprubaLaPersona() {
        UUID cliente = UUID.randomUUID();
        UUID persona = UUID.randomUUID();

        when(persistencePort.findById(cliente)).thenReturn(Optional.of(unCliente(cliente)));
        when(persistencePort.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));

        service().appointRepresentative(new AppointRepresentativeCommand(cliente, persona));

        verify(personCommunicationPort).findById(persona);
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("client.representative-appointed");
    }

    @Test
    @DisplayName("si la persona no existe no se toca el cliente")
    void personaInexistente() {
        UUID cliente = UUID.randomUUID();
        UUID persona = UUID.randomUUID();

        when(personCommunicationPort.findById(persona))
                .thenThrow(new PersonNotFoundException("No existe"));

        assertThatThrownBy(() ->
                        service().appointRepresentative(new AppointRepresentativeCommand(cliente, persona)))
                .isInstanceOf(PersonNotFoundException.class);

        verify(persistencePort, never()).save(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("agregar un correo guarda el cliente pero no publica nada")
    void agregarCorreo() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.of(unCliente(id)));
        when(persistencePort.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));

        service().addEmail(new AddClientEmailCommand(id, "contacto@hospital.com"));

        verify(persistencePort).save(any(Client.class));
        assertThat(despachados()).isEmpty();
    }

    @Test
    @DisplayName("retirar deja el cliente inactivo y publica el hecho")
    void retirar() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.of(unCliente(id)));
        when(persistencePort.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(new DeactivateClientCommand(id));

        ArgumentCaptor<Client> guardado = ArgumentCaptor.forClass(Client.class);
        verify(persistencePort).save(guardado.capture());

        assertThat(guardado.getValue().isEstadoActivo()).isFalse();
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("client.deactivated");
    }

    @Test
    @DisplayName("operar sobre un cliente que no existe falla antes de tocar nada")
    void clienteInexistente() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(new DeactivateClientCommand(id)))
                .isInstanceOf(ClientNotFoundException.class);

        verify(persistencePort, never()).save(any());
        verifyNoInteractions(dispatcher);
    }
}
