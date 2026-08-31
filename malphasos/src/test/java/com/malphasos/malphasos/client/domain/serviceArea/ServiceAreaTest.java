package com.malphasos.malphasos.client.domain.serviceArea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.client.domain.serviceArea.events.ServiceAreaCreatedEvent;
import com.malphasos.malphasos.client.domain.serviceArea.events.ServiceAreaRenamedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceAreaTest {

    private static final UUID SEDE = UUID.randomUUID();

    @Test
    @DisplayName("crear un area la deja activa y registra el hecho")
    void crear() {
        ServiceArea area = ServiceArea.create("UCI", SEDE);

        assertThat(area.getIdSede()).isEqualTo(SEDE);
        assertThat(area.isEstadoActivo()).isTrue();
        assertThat(area.pullEvents()).singleElement()
                .isInstanceOfSatisfying(ServiceAreaCreatedEvent.class, evento ->
                        assertThat(evento.metadata().eventType()).isEqualTo("service-area.created"));
    }

    @Test
    @DisplayName("un area sin sede se rechaza")
    void sedeObligatoria() {
        assertThatThrownBy(() -> ServiceArea.create("UCI", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sede");
    }

    @Test
    @DisplayName("renombrar registra el hecho; renombrar igual no emite")
    void renombrar() {
        ServiceArea area = ServiceArea.create("UCI", SEDE);
        area.pullEvents();

        area.rename("Cuidados Intensivos");
        assertThat(area.pullEvents()).singleElement().isInstanceOf(ServiceAreaRenamedEvent.class);

        area.rename("Cuidados Intensivos");
        assertThat(area.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("un area no se traslada de sede: no hay forma de pedirlo")
    void laSedeNoCambia() {
        ServiceArea area = ServiceArea.create("UCI", SEDE);
        area.rename("Otro nombre");

        assertThat(area.getIdSede()).isEqualTo(SEDE);
    }

    @Test
    @DisplayName("cerrar deja el area inactiva de forma idempotente")
    void cerrar() {
        ServiceArea area = ServiceArea.create("UCI", SEDE);
        area.pullEvents();

        area.deactivate();
        assertThat(area.isEstadoActivo()).isFalse();
        assertThat(area.pullEvents()).hasSize(1);

        area.deactivate();
        assertThat(area.pullEvents()).isEmpty();
    }
}
