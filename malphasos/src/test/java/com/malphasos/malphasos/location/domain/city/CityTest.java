package com.malphasos.malphasos.location.domain.city;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.location.domain.city.events.CityCreatedEvent;
import com.malphasos.malphasos.location.domain.city.events.CityDeactivatedEvent;
import com.malphasos.malphasos.location.domain.city.events.CityPayload;
import com.malphasos.malphasos.location.domain.city.events.CityRelocatedEvent;
import com.malphasos.malphasos.location.domain.city.events.CityRenamedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CityTest {

    private static final UUID COLOMBIA = UUID.randomUUID();

    @Test
    @DisplayName("crear una ciudad la deja activa y registra el hecho")
    void crear() {
        City ciudad = City.create("Bogota", COLOMBIA);

        assertThat(ciudad.getId()).isNotNull();
        assertThat(ciudad.getNombre()).isEqualTo("Bogota");
        assertThat(ciudad.getIdPais()).isEqualTo(COLOMBIA);
        assertThat(ciudad.isEstadoActivo()).isTrue();

        assertThat(ciudad.pullEvents()).singleElement()
                .isInstanceOfSatisfying(CityCreatedEvent.class, evento -> {
                    assertThat(evento.metadata().eventType()).isEqualTo("city.created");
                    assertThat(evento.metadata().aggregateType()).isEqualTo("City");
                    assertThat(evento.payload()).isEqualTo(new CityPayload("Bogota", COLOMBIA));
                });
    }

    @Test
    @DisplayName("dos ciudades que empiezan por las mismas letras tienen identidades distintas")
    void sinColisionPorNombre() {
        City bogota = City.create("Bogota", COLOMBIA);
        City boyaca = City.create("Boyaca", COLOMBIA);

        // El original derivaba la llave de las dos primeras letras del nombre: ambas daban "BO".
        assertThat(bogota.getId()).isNotEqualTo(boyaca.getId());
        assertThat(bogota).isNotEqualTo(boyaca);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("una ciudad sin nombre se rechaza")
    void nombreObligatorio(String nombre) {
        assertThatThrownBy(() -> City.create(nombre, COLOMBIA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    @DisplayName("una ciudad sin pais se rechaza")
    void paisObligatorio() {
        assertThatThrownBy(() -> City.create("Bogota", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pais");
    }

    @Test
    @DisplayName("renombrar y trasladar son hechos distintos, con eventos distintos")
    void renombrarYTrasladarSonHechosDistintos() {
        City ciudad = City.create("Cordoba", COLOMBIA);
        ciudad.pullEvents();

        UUID espana = UUID.randomUUID();
        ciudad.rename("Cordoba de Andalucia");
        ciudad.relocateTo(espana);

        assertThat(ciudad.pullEvents())
                .hasExactlyElementsOfTypes(CityRenamedEvent.class, CityRelocatedEvent.class)
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("city.renamed", "city.relocated");

        assertThat(ciudad.getIdPais()).isEqualTo(espana);
    }

    @Test
    @DisplayName("trasladar al mismo pais no anuncia un cambio que no ocurrio")
    void trasladarAlMismoPais() {
        City ciudad = City.create("Bogota", COLOMBIA);
        ciudad.pullEvents();

        ciudad.relocateTo(COLOMBIA);

        assertThat(ciudad.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("retirar deja la ciudad inactiva, y retirar de nuevo no repite el hecho")
    void retirar() {
        City ciudad = City.create("Bogota", COLOMBIA);
        ciudad.pullEvents();

        ciudad.deactivate();

        assertThat(ciudad.isEstadoActivo()).isFalse();
        assertThat(ciudad.pullEvents()).singleElement()
                .isInstanceOf(CityDeactivatedEvent.class);

        ciudad.deactivate();
        assertThat(ciudad.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("rehidratar no registra nada")
    void rehidratarNoEmite() {
        assertThat(City.rehydrate(UUID.randomUUID(), "Bogota", COLOMBIA, true).hasPendingEvents())
                .isFalse();
    }

    @Test
    @DisplayName("la igualdad es por identidad, no por datos")
    void igualdadPorIdentidad() {
        UUID id = UUID.randomUUID();

        assertThat(City.rehydrate(id, "Bogota", COLOMBIA, true))
                .isEqualTo(City.rehydrate(id, "Otra", UUID.randomUUID(), false));
    }
}
