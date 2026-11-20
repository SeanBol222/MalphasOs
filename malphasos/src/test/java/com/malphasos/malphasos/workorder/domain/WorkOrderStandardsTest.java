package com.malphasos.malphasos.workorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.events.WorkOrderStandardsAssignedEvent;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.events.WorkOrderStandardsAssignedPayload;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los patrones que una orden lleva para un tipo de equipo (V60): un conjunto que solo avisa si cambia. */
class WorkOrderStandardsTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID TIPO = UUID.randomUUID();

    @Test
    @DisplayName("fijarlos emite un evento con la orden, el tipo y la lista, y fijar lo mismo no emite nada")
    void fijarlos() {
        UUID pesas = UUID.randomUUID();
        WorkOrderStandards patrones = WorkOrderStandards.empty(ORDEN, TIPO);

        patrones.assign(List.of(pesas));

        assertThat(patrones.getPatrones()).containsExactly(pesas);
        assertThat(patrones.pullEvents()).singleElement().satisfies(evento -> {
            assertThat(evento.metadata().eventType()).isEqualTo(WorkOrderStandardsAssignedEvent.TYPE);
            assertThat(evento.payload()).isEqualTo(new WorkOrderStandardsAssignedPayload(ORDEN, TIPO, List.of(pesas)));
        });

        patrones.assign(List.of(pesas));
        assertThat(patrones.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("una lista vacia los quita todos; nula, con un nulo o con repetidos, se rechaza")
    void laLista() {
        UUID pesas = UUID.randomUUID();
        WorkOrderStandards patrones = WorkOrderStandards.rehydrate(ORDEN, TIPO, List.of(pesas));

        assertThatIllegalArgumentException().isThrownBy(() -> patrones.assign(null));
        assertThatIllegalArgumentException().isThrownBy(() -> patrones.assign(Arrays.asList(pesas, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> patrones.assign(List.of(pesas, pesas)))
                .withMessageContaining("dos veces");
        assertThat(patrones.getPatrones()).containsExactly(pesas);

        patrones.assign(List.of());
        assertThat(patrones.getPatrones()).isEmpty();
        assertThat(patrones.lleva(pesas)).isFalse();
    }

    @Test
    @DisplayName("leerlos de la base no emite, y su identidad es la orden con el tipo")
    void identidad() {
        WorkOrderStandards leidos = WorkOrderStandards.rehydrate(ORDEN, TIPO, List.of(UUID.randomUUID()));

        assertThat(leidos.pullEvents()).isEmpty();
        assertThat(leidos).isEqualTo(WorkOrderStandards.empty(ORDEN, TIPO))
                .isNotEqualTo(WorkOrderStandards.empty(ORDEN, UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkOrderStandards.empty(null, TIPO));
    }
}
