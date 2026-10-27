package com.malphasos.malphasos.client.domain.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.client.domain.manager.events.ManagerAssignedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerDeactivatedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagerTest {

    private static final UUID PERSONA = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();
    private static final UUID OTRA_SEDE = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();

    /** Un encargado con los eventos de su alta ya recogidos. */
    private static Manager deLaSede() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);
        encargado.pullEvents();

        return encargado;
    }

    @Test
    @DisplayName("la identidad del encargado es la de la persona, no una propia")
    void identidadCompartida() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);

        assertThat(encargado.getIdPersona()).isEqualTo(PERSONA);
        assertThat(encargado.pullEvents().getFirst().metadata().aggregateId())
                .isEqualTo(PERSONA.toString());
    }

    @Test
    @DisplayName("dos encargados sobre la misma persona son el mismo encargado")
    void igualdadPorPersona() {
        assertThat(Manager.forHeadquarter(PERSONA, SEDE))
                .isEqualTo(Manager.forServiceArea(PERSONA, AREA));
    }

    @Test
    @DisplayName("un encargado de sede tiene esa sede y ningun area")
    void encargadoDeSede() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);

        assertThat(encargado.getIdsSede()).containsExactly(SEDE);
        assertThat(encargado.getIdsAreaServicio()).isEmpty();
        assertThat(encargado.isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("un encargado de area tiene esa area y ninguna sede")
    void encargadoDeArea() {
        Manager encargado = Manager.forServiceArea(PERSONA, AREA);

        assertThat(encargado.getIdsAreaServicio()).containsExactly(AREA);
        assertThat(encargado.getIdsSede()).isEmpty();
    }

    @Test
    @DisplayName("no se puede construir un encargado sin asignacion")
    void asignacionObligatoria() {
        // En el esquema original ambas columnas eran anulables y nada las ataba al tipo, de modo
        // que cabia un encargado de sede sin sede.
        assertThatThrownBy(() -> Manager.forHeadquarter(PERSONA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asignacion");
    }

    @Test
    @DisplayName("no se puede construir un encargado sin persona")
    void personaObligatoria() {
        assertThatThrownBy(() -> Manager.forServiceArea(null, AREA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("es una persona");
    }

    @Test
    @DisplayName("asignar registra el hecho con el tipo y el destino")
    void asignarRegistra() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);

        assertThat(encargado.pullEvents()).singleElement()
                .isInstanceOfSatisfying(ManagerAssignedEvent.class, evento -> {
                    assertThat(evento.metadata().eventType()).isEqualTo("manager.assigned");
                    assertThat(evento.payload().tipo()).isEqualTo(ManagerType.HEADQUARTER);
                    assertThat(evento.payload().idAsignacion()).isEqualTo(SEDE);
                });
    }

    @Test
    @DisplayName("se suman asignaciones: varias sedes y areas a la vez (V41)")
    void sumarAsignaciones() {
        Manager encargado = deLaSede();

        encargado.assignTo(ManagerType.HEADQUARTER, OTRA_SEDE);
        encargado.assignTo(ManagerType.SERVICE_AREA, AREA);

        assertThat(encargado.getIdsSede()).containsExactly(SEDE, OTRA_SEDE);
        assertThat(encargado.getIdsAreaServicio()).containsExactly(AREA);
        assertThat(encargado.pullEvents()).hasSize(2).allMatch(ManagerAssignedEvent.class::isInstance);
    }

    @Test
    @DisplayName("asignar lo que ya tiene no cambia nada ni anuncia nada")
    void asignarLoQueYaTiene() {
        Manager encargado = deLaSede();

        encargado.assignTo(ManagerType.HEADQUARTER, SEDE);

        assertThat(encargado.getAsignaciones()).hasSize(1);
        assertThat(encargado.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("la misma sede y el mismo identificador como area son dos cosas distintas")
    void elTipoDistingue() {
        Manager encargado = deLaSede();

        encargado.assignTo(ManagerType.SERVICE_AREA, SEDE);

        assertThat(encargado.getAsignaciones()).hasSize(2);
    }

    @Test
    @DisplayName("relevar de una deja las demas, apaga esa y lo anuncia con su destino")
    void relevarDeUna() {
        Manager encargado = deLaSede();
        encargado.assignTo(ManagerType.SERVICE_AREA, AREA);
        encargado.pullEvents();

        assertThat(encargado.relieveFrom(ManagerType.HEADQUARTER, SEDE)).isTrue();

        assertThat(encargado.getIdsSede()).isEmpty();
        assertThat(encargado.getIdsAreaServicio()).containsExactly(AREA);
        assertThat(encargado.getAsignaciones())
                .contains(new ManagerAssignment(ManagerType.HEADQUARTER, SEDE, false));
        assertThat(encargado.pullEvents()).singleElement()
                .isInstanceOfSatisfying(ManagerDeactivatedEvent.class,
                        evento -> assertThat(evento.payload().idAsignacion()).isEqualTo(SEDE));
    }

    @Test
    @DisplayName("relevar de lo que ya no tenia no repite el hecho; de lo que nunca tuvo, lo dice")
    void relevarDeLoQueNoTiene() {
        Manager encargado = deLaSede();
        encargado.relieveFrom(ManagerType.HEADQUARTER, SEDE);
        encargado.pullEvents();

        assertThat(encargado.relieveFrom(ManagerType.HEADQUARTER, SEDE)).isTrue();
        assertThat(encargado.pullEvents()).isEmpty();

        assertThat(encargado.relieveFrom(ManagerType.HEADQUARTER, OTRA_SEDE)).isFalse();
    }

    @Test
    @DisplayName("volver a asignar lo relevado lo reactiva sin duplicarlo")
    void reasignarReactiva() {
        Manager encargado = deLaSede();
        encargado.relieveFrom(ManagerType.HEADQUARTER, SEDE);
        encargado.pullEvents();

        encargado.assignTo(ManagerType.HEADQUARTER, SEDE);

        assertThat(encargado.getAsignaciones())
                .containsExactly(new ManagerAssignment(ManagerType.HEADQUARTER, SEDE, true));
        assertThat(encargado.pullEvents()).singleElement().isInstanceOf(ManagerAssignedEvent.class);
    }

    @Test
    @DisplayName("relevarlo de todo apaga cada asignacion activa, una vez cada una")
    void relevarDeTodo() {
        Manager encargado = deLaSede();
        encargado.assignTo(ManagerType.SERVICE_AREA, AREA);
        encargado.pullEvents();

        encargado.deactivate();

        assertThat(encargado.isEstadoActivo()).isFalse();
        assertThat(encargado.getAsignaciones()).noneMatch(ManagerAssignment::estadoActivo);
        assertThat(encargado.pullEvents()).hasSize(2).allMatch(ManagerDeactivatedEvent.class::isInstance);

        encargado.deactivate();
        assertThat(encargado.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("las asignaciones se entregan en una copia que no se puede tocar")
    void copiaInmutable() {
        Manager encargado = deLaSede();

        assertThatThrownBy(() -> encargado.getAsignaciones().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("una asignacion sin destino se rechaza")
    void asignarSinDestino() {
        Manager encargado = deLaSede();

        assertThatThrownBy(() -> encargado.assignTo(ManagerType.SERVICE_AREA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rehidratar no registra nada")
    void rehidratar() {
        Manager encargado = Manager.rehydrate(
                PERSONA, List.of(new ManagerAssignment(ManagerType.HEADQUARTER, SEDE, true)));

        assertThat(encargado.hasPendingEvents()).isFalse();
        assertThat(encargado.getIdsSede()).containsExactly(SEDE);
    }
}
