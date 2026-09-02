package com.malphasos.malphasos.client.application.services.serviceArea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.DeactivateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.RenameServiceAreaCommand;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
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
class ServiceAreaServiceTest {

    private static final UUID SEDE = UUID.randomUUID();

    @Mock private ServiceAreaPersistencePort areaPort;
    @Mock private HeadquarterPersistencePort headquarterPort;
    @Mock private EventDispatcherPort dispatcher;

    private ServiceAreaService service() {
        return new ServiceAreaService(areaPort, headquarterPort, dispatcher);
    }

    private Headquarter sede(boolean activa) {
        return Headquarter.rehydrate(SEDE, "Sede Norte", new Address("10", "20", "30-40"),
                UUID.randomUUID(), UUID.randomUUID(), activa);
    }

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> despachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Test
    @DisplayName("abrir un area en una sede activa publica el hecho")
    void abrirArea() {
        when(headquarterPort.findById(SEDE)).thenReturn(Optional.of(sede(true)));
        when(areaPort.save(any(ServiceArea.class))).thenAnswer(i -> i.getArgument(0));

        service().create(new CreateServiceAreaCommand("UCI", SEDE));

        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("service-area.created");
    }

    @Test
    @DisplayName("no se abre un area en una sede cerrada")
    void areaEnSedeCerrada() {
        when(headquarterPort.findById(SEDE)).thenReturn(Optional.of(sede(false)));

        // La clave foranea comprueba que la sede exista, no que este activa: la regla vive aqui.
        assertThatThrownBy(() -> service().create(new CreateServiceAreaCommand("UCI", SEDE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sede cerrada");

        verify(areaPort, never()).save(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("no se abre un area en una sede que no existe")
    void areaEnSedeInexistente() {
        when(headquarterPort.findById(SEDE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(new CreateServiceAreaCommand("UCI", SEDE)))
                .isInstanceOf(HeadquarterNotFoundException.class);
    }

    @Test
    @DisplayName("renombrar publica el hecho, y renombrar igual no publica nada")
    void renombrar() {
        UUID id = UUID.randomUUID();
        when(areaPort.findById(id))
                .thenReturn(Optional.of(ServiceArea.rehydrate(id, "UCI", SEDE, true)));
        when(areaPort.save(any(ServiceArea.class))).thenAnswer(i -> i.getArgument(0));

        service().rename(new RenameServiceAreaCommand(id, "Cuidados Intensivos"));

        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("service-area.renamed");
    }

    @Test
    @DisplayName("cerrar deja el area inactiva y publica el hecho")
    void cerrar() {
        UUID id = UUID.randomUUID();
        when(areaPort.findById(id))
                .thenReturn(Optional.of(ServiceArea.rehydrate(id, "UCI", SEDE, true)));
        when(areaPort.save(any(ServiceArea.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(new DeactivateServiceAreaCommand(id));

        ArgumentCaptor<ServiceArea> guardada = ArgumentCaptor.forClass(ServiceArea.class);
        verify(areaPort).save(guardada.capture());

        assertThat(guardada.getValue().isEstadoActivo()).isFalse();
        assertThat(despachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("service-area.deactivated");
    }
}
