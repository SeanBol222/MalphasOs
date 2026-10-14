package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardCreatedEvent;
import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.referenceStandard.events.ReferenceStandardUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Un equipo patrón del catálogo (2026-10-14). */
class ReferenceStandardTest {

    private static final LocalDate VENCE = LocalDate.of(2027, 3, 31);

    private static ReferenceStandard unPatron() {
        ReferenceStandard patron = ReferenceStandard.create(" Juego de pesas F1 ", "Kern", "SN-1", "CAL-1", VENCE);
        patron.pullEvents();
        return patron;
    }

    private static List<String> tiposDe(ReferenceStandard patron) {
        return patron.pullEvents().stream().map(DomainEvent::metadata).map(m -> m.eventType()).toList();
    }

    @Test
    @DisplayName("nace vigente, con los textos recortados, y lo anuncia")
    void nace() {
        ReferenceStandard patron = ReferenceStandard.create(" Juego de pesas F1 ", "  ", "SN-1", "CAL-1", VENCE);

        assertThat(patron.getNombre()).isEqualTo("Juego de pesas F1");
        // Una marca en blanco es que no tiene: un patrón hecho en casa.
        assertThat(patron.getMarca()).isNull();
        assertThat(patron.isEstadoActivo()).isTrue();
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardCreatedEvent.TYPE);
    }

    @Test
    @DisplayName("sin nombre, serie, certificado o vencimiento no existe")
    void obligatorios() {
        assertThatIllegalArgumentException().isThrownBy(() -> ReferenceStandard.create(" ", null, "S", "C", VENCE));
        assertThatIllegalArgumentException().isThrownBy(() -> ReferenceStandard.create("N", null, "", "C", VENCE));
        assertThatIllegalArgumentException().isThrownBy(() -> ReferenceStandard.create("N", null, "S", null, VENCE));
        assertThatIllegalArgumentException().isThrownBy(() -> ReferenceStandard.create("N", null, "S", "C", null))
                .withMessageContaining("vencimiento");
    }

    @Test
    @DisplayName("un texto más largo que su columna no entra")
    void largos() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create("N", null, "x".repeat(61), "C", VENCE))
                .withMessageContaining("serie");
    }

    @Test
    @DisplayName("el día del vencimiento todavía vale; el siguiente, no")
    void vigencia() {
        ReferenceStandard patron = unPatron();

        assertThat(patron.vigenteEl(VENCE.minusDays(1))).isTrue();
        assertThat(patron.vigenteEl(VENCE)).isTrue();
        assertThat(patron.vigenteEl(VENCE.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("renovar el certificado cambia el vencimiento y lo anuncia; repetir lo mismo no")
    void renovar() {
        ReferenceStandard patron = unPatron();

        patron.update("Juego de pesas F1", "Kern", "SN-1", "CAL-2", VENCE.plusYears(1));
        assertThat(patron.getCertificado()).isEqualTo("CAL-2");
        assertThat(patron.getVencimientoCertificado()).isEqualTo(VENCE.plusYears(1));
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardUpdatedEvent.TYPE);

        patron.update("Juego de pesas F1", "Kern", "SN-1", "CAL-2", VENCE.plusYears(1));
        assertThat(tiposDe(patron)).isEmpty();
    }

    @Test
    @DisplayName("cambiar solo la marca también es un cambio, y quitarla la deja vacía")
    void soloLaMarca() {
        ReferenceStandard patron = unPatron();

        patron.update("Juego de pesas F1", "Ohaus", "SN-1", "CAL-1", VENCE);
        assertThat(patron.getMarca()).isEqualTo("Ohaus");
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardUpdatedEvent.TYPE);

        patron.update("Juego de pesas F1", null, "SN-1", "CAL-1", VENCE);
        assertThat(patron.getMarca()).isNull();
    }

    @Test
    @DisplayName("retirarlo dos veces anuncia una")
    void retirar() {
        ReferenceStandard patron = unPatron();

        patron.deactivate();
        patron.deactivate();

        assertThat(patron.isEstadoActivo()).isFalse();
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardDeactivatedEvent.TYPE);
    }
}
