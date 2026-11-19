package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.equipment.domain.verificationStandards.events.VerificationStandardsAssignedEvent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los patrones de una verificación (V60): un conjunto sin repetidos, que solo emite si cambia. */
class VerificationStandardsTest {

    private final UUID verificacion = UUID.randomUUID();
    private final UUID simulador = UUID.randomUUID();
    private final UUID manometro = UUID.randomUUID();

    @Test
    @DisplayName("asignar fija el conjunto y emite un evento con la verificacion y los patrones")
    void asignar() {
        VerificationStandards patrones = VerificationStandards.empty(verificacion);

        patrones.assign(List.of(simulador, manometro));

        assertThat(patrones.getPatrones()).containsExactlyInAnyOrder(simulador, manometro);
        assertThat(patrones.admite(simulador)).isTrue();
        assertThat(patrones.pullEvents()).singleElement()
                .isInstanceOfSatisfying(VerificationStandardsAssignedEvent.class, evento -> {
                    assertThat(evento.payload().idVerificacion()).isEqualTo(verificacion);
                    assertThat(evento.payload().patrones()).containsExactly(simulador, manometro);
                });
    }

    @Test
    @DisplayName("lo mismo en otro orden no es un cambio y no emite")
    void loMismoNoEmite() {
        VerificationStandards patrones = VerificationStandards.rehydrate(verificacion, List.of(simulador, manometro));

        patrones.assign(List.of(manometro, simulador));

        assertThat(patrones.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("la lista vacia los quita todos")
    void vaciar() {
        VerificationStandards patrones = VerificationStandards.rehydrate(verificacion, List.of(simulador));

        patrones.assign(List.of());

        assertThat(patrones.getPatrones()).isEmpty();
        assertThat(patrones.admite(simulador)).isFalse();
        assertThat(patrones.pullEvents()).hasSize(1);
    }

    @Test
    @DisplayName("un patron repetido, uno nulo o la lista que falta se rechazan sin cambiar nada")
    void listasInvalidas() {
        VerificationStandards patrones = VerificationStandards.rehydrate(verificacion, List.of(manometro));

        assertThatThrownBy(() -> patrones.assign(List.of(simulador, simulador)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dos veces");
        assertThatThrownBy(() -> patrones.assign(Arrays.asList(simulador, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> patrones.assign(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(patrones.getPatrones()).containsExactly(manometro);
        assertThat(patrones.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("sin verificacion no existe")
    void sinVerificacion() {
        assertThatThrownBy(() -> VerificationStandards.empty(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
