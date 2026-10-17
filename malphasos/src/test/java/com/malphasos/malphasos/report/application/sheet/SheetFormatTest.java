package com.malphasos.malphasos.report.application.sheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Números y fechas como los escribía el navegador con es-CO (2026-10-17). */
class SheetFormatTest {

    @Test
    @DisplayName("los números llevan coma decimal, punto de miles y hasta cuatro decimales, sin ceros de sobra")
    void numeros() {
        assertThat(SheetFormat.numero(new BigDecimal("10.5000"))).isEqualTo("10,5");
        assertThat(SheetFormat.numero(new BigDecimal("1234.56789"))).isEqualTo("1.234,5679");
        assertThat(SheetFormat.numero(new BigDecimal("0"))).isEqualTo("0");
        assertThat(SheetFormat.numero(null)).isEqualTo("0");
    }

    @Test
    @DisplayName("el signo menos es el de verdad, no un guion, y el cero no lleva signo")
    void conSigno() {
        assertThat(SheetFormat.conSigno(new BigDecimal("-0.3"))).isEqualTo("−0,3");
        assertThat(SheetFormat.conSigno(new BigDecimal("0.25"))).isEqualTo("+0,25");
        assertThat(SheetFormat.conSigno(new BigDecimal("0.0000"))).isEqualTo("0");
    }

    @Test
    @DisplayName("la fecha corta es «05 sept 2026» o «17 oct 2026», como la escribía el navegador")
    void fechas() {
        assertThat(SheetFormat.fechaCorta(LocalDate.of(2026, 10, 17))).isEqualTo("17 oct 2026");
        assertThat(SheetFormat.fechaCorta(LocalDate.of(2026, 9, 5))).isEqualTo("05 sept 2026");
        assertThat(SheetFormat.fechaCorta(LocalDate.of(2026, 3, 5))).isEqualTo("05 mar 2026");
        assertThat(SheetFormat.fechaCorta(null)).isEqualTo("—");
    }
}
