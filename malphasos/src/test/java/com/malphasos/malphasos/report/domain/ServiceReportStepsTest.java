package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStepMarkedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los pasos del protocolo dentro de un reporte (RF-13), y cómo cambian la regla de cierre.
 *
 * <p>Decisiones del usuario del 2026-10-10: con pasos, cerrar exige todos marcados y el texto es
 * opcional; sin pasos, el texto sigue siendo obligatorio; «no aplica» exige nota; y la línea de la hoja
 * de vida son los pasos hechos y detrás el trabajo adicional.
 */
class ServiceReportStepsTest {

    private static ReportStep paso(int orden, String nombre) {
        return ReportStep.copied(orden, UUID.randomUUID(), nombre, "Descripción de " + nombre, null);
    }

    private static ServiceReport conPasos(ReportStep... pasos) {
        ServiceReport reporte = ServiceReport.open(UUID.randomUUID(), UUID.randomUUID(), List.of(pasos));
        reporte.pullEvents();

        return reporte;
    }

    @Test
    @DisplayName("abre con los pasos pendientes y en su orden, aunque lleguen desordenados")
    void abreConPasos() {
        ServiceReport reporte = conPasos(paso(2, "Eléctrico"), paso(1, "Limpieza"));

        assertThat(reporte.getPasos()).extracting(ReportStep::nombre).containsExactly("Limpieza", "Eléctrico");
        assertThat(reporte.getPasos()).allMatch(p -> p.estado() == StepState.PENDIENTE);
    }

    @Test
    @DisplayName("dos pasos con el mismo orden no caben")
    void ordenRepetido() {
        assertThatThrownBy(() -> ServiceReport.open(UUID.randomUUID(), UUID.randomUUID(),
                        List.of(paso(1, "A"), paso(1, "B"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("marcar un paso lo anuncia; marcarlo igual otra vez, no")
    void marcar() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"));

        reporte.markStep(1, StepState.HECHO, null);
        reporte.markStep(1, StepState.HECHO, " ");

        assertThat(reporte.getPasos().getFirst().estado()).isEqualTo(StepState.HECHO);
        assertThat(reporte.pullEvents()).singleElement().isInstanceOf(ServiceReportStepMarkedEvent.class);
    }

    @Test
    @DisplayName("«no aplica» sin nota se rechaza: saltarse un paso queda justificado")
    void noAplicaSinNota() {
        ServiceReport reporte = conPasos(paso(1, "Batería"));

        assertThatThrownBy(() -> reporte.markStep(1, StepState.NO_APLICA, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batería");

        reporte.markStep(1, StepState.NO_APLICA, "El equipo no tiene batería");
        assertThat(reporte.getPasos().getFirst().nota()).isEqualTo("El equipo no tiene batería");
    }

    @Test
    @DisplayName("un paso se puede devolver a pendiente, y un orden que no existe se rechaza")
    void desmarcarYOrdenInexistente() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"));
        reporte.markStep(1, StepState.HECHO, null);

        reporte.markStep(1, StepState.PENDIENTE, null);

        assertThat(reporte.getPasos().getFirst().estado()).isEqualTo(StepState.PENDIENTE);
        assertThatThrownBy(() -> reporte.markStep(7, StepState.HECHO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con pasos sin marcar no se cierra, y dice cuántos faltan")
    void cerrarConPendientes() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"), paso(2, "Eléctrico"));
        reporte.fill(null, null, "Cambio de pila", null, ServiceResult.OPERATIVO);
        reporte.markStep(1, StepState.HECHO, null);

        assertThatThrownBy(reporte::finish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 paso");
        assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
    }

    @Test
    @DisplayName("con todos los pasos marcados se cierra sin trabajo adicional escrito")
    void cerrarConPasosSinTexto() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"), paso(2, "Batería"));
        reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);
        reporte.markStep(1, StepState.HECHO, null);
        reporte.markStep(2, StepState.NO_APLICA, "No tiene");

        reporte.finish();

        assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
    }

    @Test
    @DisplayName("sin pasos, el texto sigue siendo obligatorio para cerrar")
    void cerrarSinPasosExigeTexto() {
        ServiceReport reporte = conPasos();
        reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);

        assertThatThrownBy(reporte::finish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("procedimientos");
    }

    @Test
    @DisplayName("un reporte cerrado no deja marcar pasos")
    void cerradoNoSeMarca() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"));
        reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);
        reporte.markStep(1, StepState.HECHO, null);
        reporte.finish();

        assertThatThrownBy(() -> reporte.markStep(1, StepState.PENDIENTE, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("la línea de la hoja de vida: los pasos hechos en orden, y detrás el trabajo adicional")
    void lineaDeLaHojaDeVida() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"), paso(2, "Batería"), paso(3, "Eléctrico"));
        reporte.markStep(3, StepState.HECHO, null);
        reporte.markStep(1, StepState.HECHO, null);
        reporte.markStep(2, StepState.NO_APLICA, "No tiene");
        reporte.fill(null, null, "Cambio de fusible", null, null);

        // «No aplica» no sale: no se hizo.
        assertThat(reporte.descripcionDelServicio()).isEqualTo("Limpieza; Eléctrico. Además: Cambio de fusible");
    }

    @Test
    @DisplayName("sin pasos la línea es el texto escrito, como antes de V22")
    void lineaSinPasos() {
        ServiceReport reporte = conPasos();
        reporte.fill(null, null, "Limpieza general", null, null);

        assertThat(reporte.descripcionDelServicio()).isEqualTo("Limpieza general");
    }

    @Test
    @DisplayName("si no cabe en la columna de la hoja de vida se recorta con puntos suspensivos")
    void lineaLarga() {
        ServiceReport reporte = conPasos(paso(1, "Limpieza"));
        reporte.markStep(1, StepState.HECHO, null);
        reporte.fill(null, null, "x".repeat(500), null, null);

        assertThat(reporte.descripcionDelServicio()).hasSize(500).endsWith("…");
    }
}
