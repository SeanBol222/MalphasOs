package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStepMarkedEvent;
import java.util.Arrays;
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
 *
 * <p>Desde V51 un paso solo dice de qué protocolo es: su texto y su posición los dice el catálogo o el
 * documento con que el reporte se cierra, y es de ese documento de donde sale la línea de la hoja de vida.
 */
class ServiceReportStepsTest {

    private static final IntakeCheck CONFORME = new IntakeCheck(IntakeState.CONFORME, null);

    /** Un protocolo del tipo, con la posición y el nombre que el documento dirá de él. */
    private record Protocolo(UUID id, int orden, String nombre) {

        static Protocolo de(int orden, String nombre) {
            return new Protocolo(UUID.randomUUID(), orden, nombre);
        }

        ReportDocument.Paso texto() {
            return new ReportDocument.Paso(id, orden, nombre, "Descripción de " + nombre, null);
        }
    }

    private static ServiceReport conPasos(Protocolo... protocolos) {
        ServiceReport reporte = ServiceReport.open(UUID.randomUUID(), UUID.randomUUID(),
                Arrays.stream(protocolos).map(p -> ReportStep.pending(p.id())).toList());
        // Cerrar exige el ingreso desde el diseño único del informe; estas pruebas son de los pasos.
        reporte.recordIntake(CONFORME, CONFORME, List.of());
        reporte.pullEvents();

        return reporte;
    }

    /** El documento con que se cierra, con el texto de los pasos en el orden que se le dé. */
    private static ReportDocument conTextos(Protocolo... protocolos) {
        ReportDocument d = UnDocumento.CUALQUIERA;
        return new ReportDocument(d.version(), d.orden(), d.cliente(), d.equipo(), d.verificaciones(), d.empresa(),
                d.ingeniero(), d.huellaFirmaIngeniero(),
                Arrays.stream(protocolos).map(Protocolo::texto).toList(), d.patrones());
    }

    @Test
    @DisplayName("abre con los pasos pendientes, uno por protocolo")
    void abreConPasos() {
        Protocolo electrico = Protocolo.de(2, "Eléctrico");
        Protocolo limpieza = Protocolo.de(1, "Limpieza");

        ServiceReport reporte = conPasos(electrico, limpieza);

        assertThat(reporte.getPasos()).extracting(ReportStep::idProtocolo)
                .containsExactlyInAnyOrder(electrico.id(), limpieza.id());
        assertThat(reporte.getPasos()).allMatch(p -> p.estado() == StepState.PENDIENTE);
    }

    @Test
    @DisplayName("un reporte no repite protocolo: es lo que identifica a un paso desde V51")
    void protocoloRepetido() {
        UUID protocolo = UUID.randomUUID();

        assertThatThrownBy(() -> ServiceReport.open(UUID.randomUUID(), UUID.randomUUID(),
                        List.of(ReportStep.pending(protocolo), ReportStep.pending(protocolo))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocolo");
    }

    @Test
    @DisplayName("marcar un paso lo anuncia; marcarlo igual otra vez, no")
    void marcar() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        ServiceReport reporte = conPasos(limpieza);

        reporte.markStep(limpieza.id(), StepState.HECHO, null);
        reporte.markStep(limpieza.id(), StepState.HECHO, " ");

        assertThat(reporte.getPasos().getFirst().estado()).isEqualTo(StepState.HECHO);
        assertThat(reporte.pullEvents()).singleElement().isInstanceOf(ServiceReportStepMarkedEvent.class);
    }

    @Test
    @DisplayName("«no aplica» sin nota se rechaza: saltarse un paso queda justificado")
    void noAplicaSinNota() {
        Protocolo bateria = Protocolo.de(1, "Batería");
        ServiceReport reporte = conPasos(bateria);

        assertThatThrownBy(() -> reporte.markStep(bateria.id(), StepState.NO_APLICA, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nota");

        reporte.markStep(bateria.id(), StepState.NO_APLICA, "El equipo no tiene batería");
        assertThat(reporte.getPasos().getFirst().nota()).isEqualTo("El equipo no tiene batería");
    }

    @Test
    @DisplayName("un paso se puede devolver a pendiente, y un protocolo que el reporte no tiene se rechaza")
    void desmarcarYProtocoloAjeno() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        ServiceReport reporte = conPasos(limpieza);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);

        reporte.markStep(limpieza.id(), StepState.PENDIENTE, null);

        assertThat(reporte.getPasos().getFirst().estado()).isEqualTo(StepState.PENDIENTE);
        assertThatThrownBy(() -> reporte.markStep(UUID.randomUUID(), StepState.HECHO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con pasos sin marcar no se cierra, y dice cuántos faltan")
    void cerrarConPendientes() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        Protocolo electrico = Protocolo.de(2, "Eléctrico");
        ServiceReport reporte = conPasos(limpieza, electrico);
        reporte.fill(null, null, "Cambio de pila", null, ServiceResult.OPERATIVO);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);

        assertThatThrownBy(() -> reporte.finish(conTextos(limpieza, electrico)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 paso");
        assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
    }

    @Test
    @DisplayName("con todos los pasos marcados se cierra sin trabajo adicional escrito")
    void cerrarConPasosSinTexto() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        Protocolo bateria = Protocolo.de(2, "Batería");
        ServiceReport reporte = conPasos(limpieza, bateria);
        reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);
        reporte.markStep(bateria.id(), StepState.NO_APLICA, "No tiene");

        reporte.finish(conTextos(limpieza, bateria));

        assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
    }

    @Test
    @DisplayName("sin pasos, el texto sigue siendo obligatorio para cerrar")
    void cerrarSinPasosExigeTexto() {
        ServiceReport reporte = conPasos();
        reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);

        assertThatThrownBy(() -> reporte.finish(UnDocumento.CUALQUIERA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("procedimientos");
    }

    @Test
    @DisplayName("un reporte cerrado no deja marcar pasos")
    void cerradoNoSeMarca() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        ServiceReport reporte = conPasos(limpieza);
        reporte.fill(null, null, null, null, ServiceResult.OPERATIVO);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);
        reporte.finish(conTextos(limpieza));

        assertThatThrownBy(() -> reporte.markStep(limpieza.id(), StepState.PENDIENTE, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("la línea de la hoja de vida: los pasos hechos en el orden del documento, y detrás el trabajo adicional")
    void lineaDeLaHojaDeVida() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        Protocolo bateria = Protocolo.de(2, "Batería");
        Protocolo electrico = Protocolo.de(3, "Eléctrico");
        ServiceReport reporte = conPasos(electrico, bateria, limpieza);
        reporte.markStep(electrico.id(), StepState.HECHO, null);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);
        reporte.markStep(bateria.id(), StepState.NO_APLICA, "No tiene");
        reporte.fill(null, null, "Cambio de fusible", null, ServiceResult.OPERATIVO);

        reporte.finish(conTextos(limpieza, bateria, electrico));

        // «No aplica» no sale: no se hizo. Y el orden es el del documento, no el de las filas.
        assertThat(reporte.descripcionDelServicio()).isEqualTo("Limpieza; Eléctrico. Además: Cambio de fusible");
    }

    @Test
    @DisplayName("un borrador no tiene documento: su línea es solo el texto escrito")
    void lineaDeUnBorrador() {
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        ServiceReport reporte = conPasos(limpieza);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);
        reporte.fill(null, null, "Limpieza general", null, null);

        assertThat(reporte.descripcionDelServicio()).isEqualTo("Limpieza general");
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
        Protocolo limpieza = Protocolo.de(1, "Limpieza");
        ServiceReport reporte = conPasos(limpieza);
        reporte.markStep(limpieza.id(), StepState.HECHO, null);
        reporte.fill(null, null, "x".repeat(500), null, ServiceResult.OPERATIVO);
        reporte.finish(conTextos(limpieza));

        assertThat(reporte.descripcionDelServicio()).hasSize(500).endsWith("…");
    }
}
