package com.malphasos.malphasos.location.application.services.city;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.location.application.ports.output.CityPersistencePort;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.application.services.city.commands.CreateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.DeactivateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.UpdateCityCommand;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

    private static final UUID COLOMBIA = UUID.randomUUID();

    @Mock private CityPersistencePort cityPort;
    @Mock private CountryPersistencePort countryPort;
    @Mock private EventDispatcherPort dispatcher;

    private CityService service() {
        return new CityService(cityPort, countryPort, dispatcher);
    }

    private void elPaisExiste() {
        when(countryPort.findById(COLOMBIA))
                .thenReturn(Optional.of(Country.rehydrate(COLOMBIA, "COL", "Colombia", true)));
    }

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> capturarDespachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Test
    @DisplayName("crear una ciudad en un pais que existe la persiste y publica el hecho")
    void crear() {
        elPaisExiste();
        when(cityPort.save(any(City.class))).thenAnswer(i -> i.getArgument(0));

        City creada = service().create(new CreateCityCommand("Bogota", COLOMBIA));

        assertThat(creada.getNombre()).isEqualTo("Bogota");
        assertThat(capturarDespachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("city.created");
    }

    @Test
    @DisplayName("crear en un pais inexistente dice que el pais no existe, no un conflicto de datos")
    void crearEnPaisInexistente() {
        UUID fantasma = UUID.randomUUID();
        when(countryPort.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(new CreateCityCommand("Bogota", fantasma)))
                .isInstanceOf(CountryNotFoundException.class);

        verify(cityPort, never()).save(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("renombrar y trasladar en una sola peticion publica los dos hechos, en orden")
    void renombrarYTrasladar() {
        UUID id = UUID.randomUUID();
        UUID espana = UUID.randomUUID();

        when(cityPort.findById(id)).thenReturn(Optional.of(City.rehydrate(id, "Cordoba", COLOMBIA, true)));
        when(countryPort.findById(espana))
                .thenReturn(Optional.of(Country.rehydrate(espana, "ESP", "Espana", true)));
        when(cityPort.save(any(City.class))).thenAnswer(i -> i.getArgument(0));

        service().update(new UpdateCityCommand(id, "Cordoba de Andalucia", espana));

        assertThat(capturarDespachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("city.renamed", "city.relocated");
    }

    @Test
    @DisplayName("trasladar a un pais inexistente no cambia nada")
    void trasladarAPaisInexistente() {
        UUID id = UUID.randomUUID();
        UUID fantasma = UUID.randomUUID();

        when(cityPort.findById(id)).thenReturn(Optional.of(City.rehydrate(id, "Bogota", COLOMBIA, true)));
        when(countryPort.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(new UpdateCityCommand(id, null, fantasma)))
                .isInstanceOf(CountryNotFoundException.class);

        verify(cityPort, never()).save(any());
    }

    @Test
    @DisplayName("retirar deja la ciudad inactiva y la guarda, sin borrarla")
    void retirar() {
        UUID id = UUID.randomUUID();
        when(cityPort.findById(id)).thenReturn(Optional.of(City.rehydrate(id, "Bogota", COLOMBIA, true)));
        when(cityPort.save(any(City.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(new DeactivateCityCommand(id));

        ArgumentCaptor<City> guardada = ArgumentCaptor.forClass(City.class);
        verify(cityPort).save(guardada.capture());

        assertThat(guardada.getValue().isEstadoActivo()).isFalse();
        assertThat(capturarDespachados())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("city.deactivated");
    }

    @Test
    @DisplayName("listar por un pais que no existe falla en vez de devolver una lista vacia")
    void listarPorPaisInexistente() {
        UUID fantasma = UUID.randomUUID();
        when(countryPort.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findByCountry(fantasma))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    @DisplayName("operar sobre una ciudad que no existe falla antes de tocar nada")
    void ciudadInexistente() {
        UUID id = UUID.randomUUID();
        when(cityPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(new DeactivateCityCommand(id)))
                .isInstanceOf(CityNotFoundException.class);

        verifyNoInteractions(dispatcher);
    }
}
