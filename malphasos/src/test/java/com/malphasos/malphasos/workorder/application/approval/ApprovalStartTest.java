package com.malphasos.malphasos.workorder.application.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderApprovalPersistencePort;
import com.malphasos.malphasos.workorder.application.services.approval.ApprovalStart;
import com.malphasos.malphasos.workorder.application.services.approval.WorkOrderApprovalService;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Desde qué día una orden ejecutada espera aprobación (2026-11-22): el historial cargado de otro sistema
 * no la espera, y sin un corte salía entero «por aprobar».
 */
class ApprovalStartTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 11, 2);

    private static WorkOrder ejecutada(LocalDate programada, LocalDate ejecucion) {
        WorkOrderDocument documento = ejecucion == null
                ? null
                : new WorkOrderDocument(WorkOrderDocument.VERSION, "ABC123", "PREVENTIVO", false, "ANUAL",
                        programada, ejecucion, new WorkOrderDocument.Cliente("Cliente", "900"),
                        new WorkOrderDocument.Sede("Sede", "Calle 1", "Bogota"), "Ingeniero", List.of());

        return WorkOrder.rehydrate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), programada,
                Periodicity.ANUAL, ServiceType.PREVENTIVO, false, ExecutionState.EJECUTADA, UUID.randomUUID(),
                true, documento, false);
    }

    @Test
    @DisplayName("sin fecha no hay corte: toda ejecutada espera")
    void sinCorte() {
        ApprovalStart sinFecha = new ApprovalStart("");

        assertThat(sinFecha.desde()).isEmpty();
        assertThat(sinFecha.awaits(ejecutada(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1)))).isTrue();
    }

    @Test
    @DisplayName("con fecha, espera la ejecutada ese día o después, y no la de antes")
    void conCorte() {
        ApprovalStart corte = new ApprovalStart("2026-11-02");

        assertThat(corte.desde()).contains(CORTE);
        assertThat(corte.awaits(ejecutada(CORTE.minusDays(5), CORTE))).isTrue();
        assertThat(corte.awaits(ejecutada(CORTE.minusDays(5), CORTE.minusDays(1)))).isFalse();
    }

    @Test
    @DisplayName("cuenta el día de ejecución, no el programado: una tardía ejecutada después del corte espera")
    void cuentaLaEjecucion() {
        ApprovalStart corte = new ApprovalStart("2026-11-02");

        assertThat(corte.awaits(ejecutada(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 3)))).isTrue();
    }

    @Test
    @DisplayName("una congelada tarde, sin día de ejecución, cuenta por el programado")
    void sinDiaDeEjecucion() {
        ApprovalStart corte = new ApprovalStart("2026-11-02");

        assertThat(corte.awaits(ejecutada(LocalDate.of(2026, 10, 5), null))).isFalse();
        assertThat(corte.awaits(ejecutada(LocalDate.of(2026, 11, 5), null))).isTrue();
    }

    @Test
    @DisplayName("una fecha mal escrita tumba el arranque en vez de no cortar")
    void fechaMalEscrita() {
        assertThatThrownBy(() -> new ApprovalStart("02/11/2026")).isInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("las pendientes del servicio dejan fuera lo ejecutado antes del corte")
    void elServicioCorta() {
        WorkOrderServicePort ordenes = mock(WorkOrderServicePort.class);
        WorkOrderApprovalPersistencePort aprobaciones = mock(WorkOrderApprovalPersistencePort.class);
        WorkOrder vieja = ejecutada(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        WorkOrder nueva = ejecutada(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        when(ordenes.findAll(any())).thenReturn(List.of(vieja, nueva));
        when(aprobaciones.findApprovedAmong(any())).thenReturn(Set.of());
        WorkOrderApprovalService servicio = new WorkOrderApprovalService(
                aprobaciones, ordenes, null, null, null, null, null, new ApprovalStart("2026-11-02"));

        assertThat(servicio.pending(ReadScope.unrestricted())).containsExactly(nueva);
    }
}
