package com.malphasos.malphasos.client.domain.headquarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.client.domain.headquarter.events.HeadquarterCreatedEvent;
import com.malphasos.malphasos.client.domain.headquarter.events.HeadquarterUpdatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HeadquarterTest {

    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID CIUDAD = UUID.randomUUID();

    private Headquarter unaSede() {
        return Headquarter.create("Sede Norte", new Address("10", "20", "30-40"), CLIENTE, CIUDAD);
    }

    @Test
    @DisplayName("crear una sede la deja activa y registra el hecho")
    void crear() {
        Headquarter sede = unaSede();

        assertThat(sede.isEstadoActivo()).isTrue();
        assertThat(sede.getIdCliente()).isEqualTo(CLIENTE);
        assertThat(sede.pullEvents()).singleElement()
                .isInstanceOfSatisfying(HeadquarterCreatedEvent.class, evento ->
                        assertThat(evento.metadata().aggregateType()).isEqualTo("Headquarter"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("una direccion incompleta se rechaza")
    void direccionIncompleta(String vacio) {
        // En el original eran tres campos sueltos de la sede: cabia una con calle y sin numero.
        assertThatThrownBy(() -> new Address("10", "20", vacio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numero");
    }

    @Test
    @DisplayName("una sede necesita cliente y ciudad")
    void clienteYCiudadObligatorios() {
        Address direccion = new Address("10", "20", "30-40");

        assertThatThrownBy(() -> Headquarter.create("Sede", direccion, null, CIUDAD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cliente");
        assertThatThrownBy(() -> Headquarter.create("Sede", direccion, CLIENTE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ciudad");
    }

    @Test
    @DisplayName("actualizar la direccion registra el hecho")
    void actualizarDireccion() {
        Headquarter sede = unaSede();
        sede.pullEvents();

        sede.update(null, new Address("50", "60", "70-80"), null);

        assertThat(sede.getDireccion().calle()).isEqualTo("50");
        assertThat(sede.pullEvents()).singleElement().isInstanceOf(HeadquarterUpdatedEvent.class);
    }

    @Test
    @DisplayName("actualizar sin cambios reales no emite")
    void actualizarSinCambios() {
        Headquarter sede = unaSede();
        sede.pullEvents();

        sede.update("Sede Norte", new Address("10", "20", "30-40"), CIUDAD);

        assertThat(sede.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("una sede no cambia de cliente: no hay forma de pedirlo")
    void elClienteNoCambia() {
        Headquarter sede = unaSede();
        sede.update("Otro nombre", null, UUID.randomUUID());

        assertThat(sede.getIdCliente()).isEqualTo(CLIENTE);
    }

    @Test
    @DisplayName("cerrar deja la sede inactiva, y cerrar de nuevo no repite el hecho")
    void cerrar() {
        Headquarter sede = unaSede();
        sede.pullEvents();

        sede.deactivate();
        assertThat(sede.isEstadoActivo()).isFalse();
        assertThat(sede.pullEvents()).hasSize(1);

        sede.deactivate();
        assertThat(sede.pullEvents()).isEmpty();
    }
}
