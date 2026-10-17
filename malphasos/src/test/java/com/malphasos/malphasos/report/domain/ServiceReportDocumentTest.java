package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El informe se congela al cerrar (V37, 2026-10-17). */
class ServiceReportDocumentTest {

    private static final IntakeCheck CONFORME = new IntakeCheck(IntakeState.CONFORME, null);

    private static ServiceReport cerrable() {
        ServiceReport reporte = ServiceReport.open(UUID.randomUUID(), UUID.randomUUID());
        reporte.recordIntake(CONFORME, CONFORME, List.of());
        reporte.fill("No enciende", "Fusible", "Limpieza", null, ServiceResult.OPERATIVO);
        reporte.pullEvents();
        return reporte;
    }

    /** Uno cerrado antes de V37: cerrado y sin documento. */
    private static ServiceReport cerradoSinDocumento() {
        return ServiceReport.rehydrate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ReportState.FINALIZADO, null, null, "Limpieza", null, null, null, List.of(), CONFORME, CONFORME,
                List.of(), null, ServiceResult.OPERATIVO, LocalDateTime.of(2026, 10, 1, 9, 0), List.of(), List.of(),
                true, null, false);
    }

    @Test
    @DisplayName("un borrador no tiene documento: lo compone en vivo quien lo pide")
    void borrador() {
        assertThat(cerrable().getDocumento()).isNull();
    }

    @Test
    @DisplayName("cerrar guarda el documento que recibe, y no es tardío")
    void cerrarLoCongela() {
        ServiceReport reporte = cerrable();

        reporte.finish(UnDocumento.CUALQUIERA);

        assertThat(reporte.getDocumento()).isEqualTo(UnDocumento.CUALQUIERA);
        assertThat(reporte.isDocumentoTardio()).isFalse();
    }

    @Test
    @DisplayName("el cierre del correctivo también lo congela")
    void correctivo() {
        ServiceReport reporte = cerrable();

        reporte.finishCorrective(UnDocumento.CUALQUIERA);

        assertThat(reporte.getDocumento()).isEqualTo(UnDocumento.CUALQUIERA);
    }

    @Test
    @DisplayName("sin documento no se cierra, y el reporte sigue en borrador")
    void sinDocumento() {
        ServiceReport reporte = cerrable();

        assertThatNullPointerException().isThrownBy(() -> reporte.finish(null));
        assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
    }

    @Test
    @DisplayName("uno cerrado antes de V37 se congela tarde, y queda marcado; sin evento")
    void congelarTarde() {
        ServiceReport reporte = cerradoSinDocumento();

        reporte.freezeLate(UnDocumento.CUALQUIERA);

        assertThat(reporte.getDocumento()).isEqualTo(UnDocumento.CUALQUIERA);
        assertThat(reporte.isDocumentoTardio()).isTrue();
        assertThat(reporte.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("un borrador no se congela tarde: se congela al cerrar")
    void borradorNoSeCongelaTarde() {
        assertThatIllegalStateException().isThrownBy(() -> cerrable().freezeLate(UnDocumento.CUALQUIERA))
                .withMessageContaining("borrador");
    }

    @Test
    @DisplayName("uno que ya tiene documento no se vuelve a congelar: la copia del cierre no se pisa")
    void unaSolaVez() {
        ServiceReport reporte = cerrable();
        reporte.finish(UnDocumento.CUALQUIERA);
        ReportDocument otro = new ReportDocument(ReportDocument.VERSION, UnDocumento.CUALQUIERA.orden(),
                UnDocumento.CUALQUIERA.cliente(), UnDocumento.CUALQUIERA.equipo(), List.of(), null, "Otra persona");

        assertThatIllegalStateException().isThrownBy(() -> reporte.freezeLate(otro));
        assertThat(reporte.getDocumento()).isEqualTo(UnDocumento.CUALQUIERA);
    }
}
