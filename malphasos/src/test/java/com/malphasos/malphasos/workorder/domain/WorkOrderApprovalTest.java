package com.malphasos.malphasos.workorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import com.malphasos.malphasos.workorder.domain.approval.events.WorkOrderApprovalPayload;
import com.malphasos.malphasos.workorder.domain.approval.events.WorkOrderApprovalWithdrawnEvent;
import com.malphasos.malphasos.workorder.domain.approval.events.WorkOrderApprovedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La aprobación de una orden (V54): lo que decide sola, sin mirar fuera. */
class WorkOrderApprovalTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();

    private static WorkOrderApproval aprobada(UUID operador) {
        return WorkOrderApproval.approve(ORDEN, SUPERVISOR, operador, "a".repeat(64), LocalDateTime.now());
    }

    @Test
    @DisplayName("aprobar la deja vigente y anuncia quién la dio y de qué forma")
    void aprobar() {
        WorkOrderApproval aprobacion = aprobada(SUPERVISOR);

        assertThat(aprobacion.isEstadoActivo()).isTrue();
        assertThat(aprobacion.pullEvents()).singleElement()
                .isInstanceOfSatisfying(WorkOrderApprovedEvent.class, evento -> assertThat(evento.payload())
                        .isEqualTo(new WorkOrderApprovalPayload(ORDEN, SUPERVISOR, true)));
    }

    @Test
    @DisplayName("la forma se deduce: desde su cuenta si la sesión era suya; en sitio si era de otro o de nadie")
    void laFormaSeDeduce() {
        assertThat(aprobada(SUPERVISOR).fromOwnAccount()).isTrue();
        assertThat(aprobada(UUID.randomUUID()).fromOwnAccount()).isFalse();
        assertThat(aprobada(null).fromOwnAccount()).isFalse();
    }

    @Test
    @DisplayName("retirar la deja sin vigencia, y retirar dos veces no repite el hecho")
    void retirar() {
        WorkOrderApproval aprobacion = aprobada(SUPERVISOR);
        aprobacion.pullEvents();

        aprobacion.withdraw();

        assertThat(aprobacion.isEstadoActivo()).isFalse();
        assertThat(aprobacion.pullEvents()).singleElement().isInstanceOf(WorkOrderApprovalWithdrawnEvent.class);
        aprobacion.withdraw();
        assertThat(aprobacion.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("sin orden, sin supervisor o sin firma no hay aprobación")
    void loQueExige() {
        assertThatThrownBy(() -> WorkOrderApproval.approve(null, SUPERVISOR, null, "h", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkOrderApproval.approve(ORDEN, null, null, "h", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkOrderApproval.approve(ORDEN, SUPERVISOR, null, null, LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rehidratar no anuncia nada, y la igualdad es por identidad")
    void rehidratar() {
        UUID id = UUID.randomUUID();
        WorkOrderApproval una = WorkOrderApproval.rehydrate(id, ORDEN, SUPERVISOR, null, "h", LocalDateTime.now(), true);
        WorkOrderApproval otra = WorkOrderApproval.rehydrate(id, UUID.randomUUID(), SUPERVISOR, null, "x",
                LocalDateTime.now(), false);

        assertThat(una.pullEvents()).isEmpty();
        assertThat(una).isEqualTo(otra);
    }
}
