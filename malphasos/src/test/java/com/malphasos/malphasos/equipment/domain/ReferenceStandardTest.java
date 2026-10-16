package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
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
    private static final ReferenceStandardSheet SIN_FICHA = ReferenceStandardSheet.VACIA;

    private static ReferenceStandard unPatron() {
        ReferenceStandard patron =
                ReferenceStandard.create(" Juego de pesas F1 ", "Kern", "SN-1", "CAL-1", VENCE, SIN_FICHA);
        patron.pullEvents();
        return patron;
    }

    private static List<String> tiposDe(ReferenceStandard patron) {
        return patron.pullEvents().stream().map(DomainEvent::metadata).map(m -> m.eventType()).toList();
    }

    @Test
    @DisplayName("nace vigente, con los textos recortados, y lo anuncia")
    void nace() {
        ReferenceStandard patron =
                ReferenceStandard.create(" Juego de pesas F1 ", "  ", "SN-1", "CAL-1", VENCE, SIN_FICHA);

        assertThat(patron.getNombre()).isEqualTo("Juego de pesas F1");
        // Una marca en blanco es que no tiene: un patrón hecho en casa.
        assertThat(patron.getMarca()).isNull();
        assertThat(patron.isEstadoActivo()).isTrue();
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardCreatedEvent.TYPE);
    }

    @Test
    @DisplayName("sin nombre, serie, certificado o vencimiento no existe")
    void obligatorios() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create(" ", null, "S", "C", VENCE, SIN_FICHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create("N", null, "", "C", VENCE, SIN_FICHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create("N", null, "S", null, VENCE, SIN_FICHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create("N", null, "S", "C", null, SIN_FICHA))
                .withMessageContaining("vencimiento");
    }

    @Test
    @DisplayName("un texto más largo que su columna no entra")
    void largos() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create("N", null, "x".repeat(61), "C", VENCE, SIN_FICHA))
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

        patron.update("Juego de pesas F1", "Kern", "SN-1", "CAL-2", VENCE.plusYears(1), SIN_FICHA);
        assertThat(patron.getCertificado()).isEqualTo("CAL-2");
        assertThat(patron.getVencimientoCertificado()).isEqualTo(VENCE.plusYears(1));
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardUpdatedEvent.TYPE);

        patron.update("Juego de pesas F1", "Kern", "SN-1", "CAL-2", VENCE.plusYears(1), SIN_FICHA);
        assertThat(tiposDe(patron)).isEmpty();
    }

    @Test
    @DisplayName("cambiar solo la marca también es un cambio, y quitarla la deja vacía")
    void soloLaMarca() {
        ReferenceStandard patron = unPatron();

        patron.update("Juego de pesas F1", "Ohaus", "SN-1", "CAL-1", VENCE, SIN_FICHA);
        assertThat(patron.getMarca()).isEqualTo("Ohaus");
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardUpdatedEvent.TYPE);

        patron.update("Juego de pesas F1", null, "SN-1", "CAL-1", VENCE, SIN_FICHA);
        assertThat(patron.getMarca()).isNull();
    }

    @Test
    @DisplayName("la ficha llega recortada, y un dato en blanco es uno que falta")
    void laFicha() {
        ReferenceStandardSheet ficha = new ReferenceStandardSheet(" MS-200 ", "  ", " 1 g a 2 kg ", "0,1 mg",
                VENCE.minusYears(1), " Metrología Andina ");

        ReferenceStandard patron = ReferenceStandard.create("Pesas", null, "SN-1", "CAL-1", VENCE, ficha);

        assertThat(patron.getFicha()).isEqualTo(new ReferenceStandardSheet(
                "MS-200", null, "1 g a 2 kg", "0,1 mg", VENCE.minusYears(1), "Metrología Andina"));
    }

    @Test
    @DisplayName("un dato de la ficha más largo que su columna no entra")
    void fichaLarga() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReferenceStandardSheet(null, "x".repeat(41), null, null, null, null))
                .withMessageContaining("código interno");
    }

    @Test
    @DisplayName("se puede calibrar el día que vence el certificado, pero no después")
    void calibracionYVencimiento() {
        ReferenceStandardSheet elMismoDia = new ReferenceStandardSheet(null, null, null, null, VENCE, null);
        ReferenceStandardSheet despues = new ReferenceStandardSheet(null, null, null, null, VENCE.plusDays(1), null);

        assertThat(ReferenceStandard.create("N", null, "S", "C", VENCE, elMismoDia).getFicha().fechaCalibracion())
                .isEqualTo(VENCE);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReferenceStandard.create("N", null, "S", "C", VENCE, despues))
                .withMessageContaining("posterior");
        // Y al corregirlo, que es donde de verdad pasa: se renueva la fecha y se olvida el vencimiento.
        ReferenceStandard patron = unPatron();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> patron.update("N", null, "S", "C", VENCE, despues));
        assertThat(patron.getFicha()).isEqualTo(ReferenceStandardSheet.VACIA);
    }

    @Test
    @DisplayName("cambiar solo la ficha también es un cambio; repetirla no")
    void soloLaFicha() {
        ReferenceStandard patron = unPatron();
        ReferenceStandardSheet ficha = new ReferenceStandardSheet(null, "BB-PAT-003", null, null, null, null);

        patron.update("Juego de pesas F1", "Kern", "SN-1", "CAL-1", VENCE, ficha);
        assertThat(patron.getFicha().codigoInterno()).isEqualTo("BB-PAT-003");
        assertThat(tiposDe(patron)).containsExactly(ReferenceStandardUpdatedEvent.TYPE);

        patron.update("Juego de pesas F1", "Kern", "SN-1", "CAL-1", VENCE,
                new ReferenceStandardSheet(null, " BB-PAT-003 ", null, null, null, null));
        assertThat(tiposDe(patron)).isEmpty();
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
