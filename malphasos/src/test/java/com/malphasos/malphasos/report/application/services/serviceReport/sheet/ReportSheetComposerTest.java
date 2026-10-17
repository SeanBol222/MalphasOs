package com.malphasos.malphasos.report.application.services.serviceReport.sheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que el compositor deriva en vez de guardar (2026-10-17). Lo que imprime lo fija
 * {@code ReportHtmlRendererTest}; aquí, los bordes del próximo servicio, que son los de
 * {@code documento-reporte.spec.ts}.
 */
class ReportSheetComposerTest {

    @Test
    @DisplayName("el próximo servicio suma la periodicidad en meses de calendario")
    void sumaLaPeriodicidad() {
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2026, 1, 7), "SEMESTRAL"))
                .isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2026, 10, 15), "TRIMESTRAL"))
                .isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2026, 12, 31), "MENSUAL"))
                .isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2026, 3, 1), "ANUAL"))
                .isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    @DisplayName("se recorta al fin de mes: el 31 de agosto más seis meses es el último día de febrero")
    void finDeMes() {
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2026, 8, 31), "SEMESTRAL"))
                .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2028, 2, 29), "ANUAL"))
                .isEqualTo(LocalDate.of(2029, 2, 28));
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2027, 8, 31), "SEMESTRAL"))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("sin fecha o sin periodicidad no hay próximo servicio")
    void sinDatos() {
        assertThat(ReportSheetComposer.proximoServicio(null, "ANUAL")).isNull();
        assertThat(ReportSheetComposer.proximoServicio(LocalDate.of(2026, 1, 7), null)).isNull();
    }
}
