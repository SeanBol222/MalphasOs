package com.malphasos.malphasos.location.domain.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.location.domain.country.events.CountryCreatedEvent;
import com.malphasos.malphasos.location.domain.country.events.CountryDeactivatedEvent;
import com.malphasos.malphasos.location.domain.country.events.CountryPayload;
import com.malphasos.malphasos.location.domain.country.events.CountryRenamedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CountryTest {

    @Test
    @DisplayName("crear un pais lo deja activo y registra el hecho")
    void crear() {
        Country pais = Country.create("COL", "Colombia");

        assertThat(pais.getId()).isNotNull();
        assertThat(pais.getCodigoIso()).isEqualTo("COL");
        assertThat(pais.getNombre()).isEqualTo("Colombia");
        assertThat(pais.isEstadoActivo()).isTrue();

        assertThat(pais.pullEvents()).singleElement()
                .isInstanceOfSatisfying(CountryCreatedEvent.class, evento -> {
                    assertThat(evento.metadata().eventType()).isEqualTo("country.created");
                    assertThat(evento.metadata().aggregateType()).isEqualTo("Country");
                    assertThat(evento.metadata().aggregateId()).isEqualTo(pais.getId().toString());
                    assertThat(evento.payload()).isEqualTo(new CountryPayload("COL", "Colombia"));
                });
    }

    @Test
    @DisplayName("el codigo ISO se normaliza a mayusculas y sin espacios")
    void codigoNormalizado() {
        assertThat(Country.create(" col ", "Colombia").getCodigoIso()).isEqualTo("COL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CO", "COLO", "C0L", "co-"})
    @DisplayName("un codigo que no sean tres letras se rechaza")
    void codigoInvalido(String codigo) {
        assertThatThrownBy(() -> Country.create(codigo, "Colombia"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("codigo ISO");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("un pais sin nombre se rechaza")
    void nombreObligatorio(String nombre) {
        assertThatThrownBy(() -> Country.create("COL", nombre))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    @DisplayName("renombrar cambia el nombre y registra el hecho")
    void renombrar() {
        Country pais = Country.create("COL", "Colombia");
        pais.pullEvents();

        pais.rename("Republica de Colombia");

        assertThat(pais.getNombre()).isEqualTo("Republica de Colombia");
        assertThat(pais.pullEvents()).singleElement()
                .isInstanceOfSatisfying(CountryRenamedEvent.class, evento ->
                        assertThat(evento.metadata().eventType()).isEqualTo("country.renamed"));
    }

    @Test
    @DisplayName("renombrar con el mismo nombre no anuncia un cambio que no ocurrio")
    void renombrarConElMismoNombre() {
        Country pais = Country.create("COL", "Colombia");
        pais.pullEvents();

        pais.rename("Colombia");

        assertThat(pais.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("retirar deja el pais inactivo y registra el hecho")
    void retirar() {
        Country pais = Country.create("COL", "Colombia");
        pais.pullEvents();

        pais.deactivate();

        assertThat(pais.isEstadoActivo()).isFalse();
        assertThat(pais.pullEvents()).singleElement()
                .isInstanceOfSatisfying(CountryDeactivatedEvent.class, evento ->
                        assertThat(evento.metadata().eventType()).isEqualTo("country.deactivated"));
    }

    @Test
    @DisplayName("retirar dos veces solo registra el hecho una vez")
    void retirarDosVeces() {
        Country pais = Country.create("COL", "Colombia");
        pais.deactivate();
        pais.pullEvents();

        pais.deactivate();

        assertThat(pais.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("rehidratar no registra nada: leer de la base no es un hecho del dominio")
    void rehidratarNoEmite() {
        Country pais = Country.rehydrate(UUID.randomUUID(), "COL", "Colombia", true);

        assertThat(pais.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("dos paises con el mismo identificador son iguales aunque difieran sus datos")
    void igualdadPorIdentidad() {
        UUID id = UUID.randomUUID();

        Country uno = Country.rehydrate(id, "COL", "Colombia", true);
        Country otro = Country.rehydrate(id, "COL", "Republica de Colombia", false);

        // Con @EqualsAndHashCode(callSuper = true) del original, esta comparacion delegaba en la
        // igualdad por referencia de Object y era siempre falsa.
        assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);
    }

    @Test
    @DisplayName("dos paises distintos con los mismos datos no son iguales")
    void identidadesDistintas() {
        assertThat(Country.create("COL", "Colombia")).isNotEqualTo(Country.create("COL", "Colombia"));
    }
}
