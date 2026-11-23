package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.equipment.domain.externalIntervention.events.ExternalInterventionDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.externalIntervention.events.ExternalInterventionRecordedEvent;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que se le hizo a un equipo fuera de MalphasOS (V61, 2026-11-23). */
class ExternalInterventionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 23);
    private static final UUID EQUIPO = UUID.randomUUID();

    private static ExternalIntervention una(LocalDate dia, String descripcion, String empresa, String documento) {
        return ExternalIntervention.create(EQUIPO, dia, ExternalServiceType.CALIBRACION, descripcion, empresa,
                null, documento, HOY);
    }

    @Test
    @DisplayName("se anota con lo mínimo, recortada y activa, y lo cuenta un evento")
    void conLoMinimo() {
        ExternalIntervention intervencion = una(HOY, "  Calibración  ", " Metrología S.A.S. ", "   ");

        assertThat(intervencion.getDescripcion()).isEqualTo("Calibración");
        assertThat(intervencion.getEmpresa()).isEqualTo("Metrología S.A.S.");
        assertThat(intervencion.getDocumento()).isNull();
        assertThat(intervencion.getResultado()).isNull();
        assertThat(intervencion.isEstadoActivo()).isTrue();
        assertThat(intervencion.pullEvents()).singleElement().isInstanceOf(ExternalInterventionRecordedEvent.class);
    }

    @Test
    @DisplayName("hoy vale, mañana no: lo que pasó no puede ser futuro")
    void sinFechasFuturas() {
        assertThatThrownBy(() -> una(HOY.plusDays(1), "Calibración", "Otra", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");
    }

    @Test
    @DisplayName("qué se hizo y quién lo hizo son obligatorios, y tienen su largo")
    void obligatorios() {
        assertThatThrownBy(() -> una(HOY, " ", "Otra", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> una(HOY, "Calibración", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> una(HOY, "x".repeat(501), "Otra", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> una(HOY, "Calibración", "Otra", "x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin fecha, sin tipo o sin equipo no se anota")
    void sinLoQueLaIdentifica() {
        assertThatThrownBy(() -> una(null, "Calibración", "Otra", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalIntervention.create(EQUIPO, HOY, null, "Calibración", "Otra", null, null, HOY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalIntervention.create(null, HOY, ExternalServiceType.OTRO, "Algo", "Otra",
                        InterventionResult.OPERATIVO, null, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retirarla dos veces emite un solo evento")
    void retirar() {
        ExternalIntervention intervencion = una(HOY, "Calibración", "Otra", null);
        intervencion.pullEvents();

        intervencion.deactivate();
        intervencion.deactivate();

        assertThat(intervencion.isEstadoActivo()).isFalse();
        assertThat(intervencion.pullEvents()).singleElement().isInstanceOf(ExternalInterventionDeactivatedEvent.class);
    }
}
