package com.malphasos.malphasos.client.domain.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.client.domain.manager.events.ManagerAssignedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerDeactivatedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerReassignedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagerTest {

    private static final UUID PERSONA = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();

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
    @DisplayName("un encargado de sede tiene sede y no tiene area")
    void encargadoDeSede() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);

        assertThat(encargado.getTipo()).isEqualTo(ManagerType.HEADQUARTER);
        assertThat(encargado.getIdSede()).isEqualTo(SEDE);
        assertThat(encargado.getIdAreaServicio()).isNull();
    }

    @Test
    @DisplayName("un encargado de area tiene area y no tiene sede")
    void encargadoDeArea() {
        Manager encargado = Manager.forServiceArea(PERSONA, AREA);

        assertThat(encargado.getTipo()).isEqualTo(ManagerType.SERVICE_AREA);
        assertThat(encargado.getIdAreaServicio()).isEqualTo(AREA);
        assertThat(encargado.getIdSede()).isNull();
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
    @DisplayName("un encargado de sede puede pasar a serlo de un area")
    void reasignarCambiandoDeTipo() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);
        encargado.pullEvents();

        encargado.reassignTo(ManagerType.SERVICE_AREA, AREA);

        assertThat(encargado.getTipo()).isEqualTo(ManagerType.SERVICE_AREA);
        assertThat(encargado.getIdSede()).isNull();
        assertThat(encargado.getIdAreaServicio()).isEqualTo(AREA);
        assertThat(encargado.pullEvents()).singleElement().isInstanceOf(ManagerReassignedEvent.class);
    }

    @Test
    @DisplayName("reasignar al mismo sitio no anuncia un cambio que no ocurrio")
    void reasignarAlMismoSitio() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);
        encargado.pullEvents();

        encargado.reassignTo(ManagerType.HEADQUARTER, SEDE);

        assertThat(encargado.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("una reasignacion sin destino se rechaza")
    void reasignarSinDestino() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);

        assertThatThrownBy(() -> encargado.reassignTo(ManagerType.SERVICE_AREA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("relevar deja el encargado inactivo, y relevar de nuevo no repite el hecho")
    void relevar() {
        Manager encargado = Manager.forHeadquarter(PERSONA, SEDE);
        encargado.pullEvents();

        encargado.deactivate();

        assertThat(encargado.isEstadoActivo()).isFalse();
        assertThat(encargado.pullEvents()).singleElement().isInstanceOf(ManagerDeactivatedEvent.class);

        encargado.deactivate();
        assertThat(encargado.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("rehidratar no registra nada")
    void rehidratar() {
        assertThat(Manager.rehydrate(PERSONA, ManagerType.HEADQUARTER, SEDE, true).hasPendingEvents())
                .isFalse();
    }
}
