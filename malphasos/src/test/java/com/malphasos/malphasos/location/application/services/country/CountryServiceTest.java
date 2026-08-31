package com.malphasos.malphasos.location.application.services.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.application.services.country.commands.CreateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.DeactivateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.UpdateCountryCommand;
import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.domain.exception.CountryNotFoundException;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** Orquestación del servicio, con los puertos de salida sustituidos por dobles. */
@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock private CountryPersistencePort persistencePort;
    @Mock private EventDispatcherPort dispatcher;

    private CountryService service() {
        return new CountryService(persistencePort, dispatcher);
    }

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> capturarDespachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Test
    @DisplayName("crear persiste el pais y publica lo que el agregado registro")
    void crear() {
        when(persistencePort.save(any(Country.class))).thenAnswer(i -> i.getArgument(0));

        Country creado = service().create(new CreateCountryCommand("COL", "Colombia"));

        assertThat(creado.getCodigoIso()).isEqualTo("COL");
        assertThat(capturarDespachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("country.created");
    }

    @Test
    @DisplayName("se persiste antes de publicar: nunca se anuncia un cambio que la base rechazo")
    void persistirAntesDePublicar() {
        when(persistencePort.save(any(Country.class))).thenAnswer(i -> i.getArgument(0));

        service().create(new CreateCountryCommand("COL", "Colombia"));

        InOrder orden = Mockito.inOrder(persistencePort, dispatcher);
        orden.verify(persistencePort).save(any(Country.class));
        orden.verify(dispatcher).dispatchAll(any());
    }

    @Test
    @DisplayName("si el almacen falla no se publica nada")
    void fallarAlPersistirNoPublica() {
        when(persistencePort.save(any(Country.class))).thenThrow(new RuntimeException("caida"));

        assertThatThrownBy(() -> service().create(new CreateCountryCommand("COL", "Colombia")))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("actualizar el nombre publica el hecho de renombrado")
    void actualizar() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id))
                .thenReturn(Optional.of(Country.rehydrate(id, "COL", "Colombia", true)));
        when(persistencePort.save(any(Country.class))).thenAnswer(i -> i.getArgument(0));

        service().update(new UpdateCountryCommand(id, "Republica de Colombia"));

        assertThat(capturarDespachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("country.renamed");
    }

    @Test
    @DisplayName("un nombre nulo significa dejarlo como esta, y no publica nada")
    void actualizarSinNombre() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id))
                .thenReturn(Optional.of(Country.rehydrate(id, "COL", "Colombia", true)));
        when(persistencePort.save(any(Country.class))).thenAnswer(i -> i.getArgument(0));

        service().update(new UpdateCountryCommand(id, null));

        assertThat(capturarDespachados()).isEmpty();
    }

    @Test
    @DisplayName("retirar deja el pais inactivo y lo guarda, sin borrarlo")
    void retirar() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id))
                .thenReturn(Optional.of(Country.rehydrate(id, "COL", "Colombia", true)));
        when(persistencePort.save(any(Country.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(new DeactivateCountryCommand(id));

        ArgumentCaptor<Country> guardado = ArgumentCaptor.forClass(Country.class);
        verify(persistencePort).save(guardado.capture());

        assertThat(guardado.getValue().isEstadoActivo()).isFalse();
        assertThat(capturarDespachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("country.deactivated");
    }

    @Test
    @DisplayName("operar sobre un pais que no existe falla antes de tocar nada")
    void paisInexistente() {
        UUID id = UUID.randomUUID();
        when(persistencePort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(new UpdateCountryCommand(id, "Otro")))
                .isInstanceOf(CountryNotFoundException.class);

        verify(persistencePort, never()).save(any());
        verifyNoInteractions(dispatcher);
    }
}
