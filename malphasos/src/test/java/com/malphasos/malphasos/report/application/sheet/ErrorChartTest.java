package com.malphasos.malphasos.report.application.sheet;

import static com.malphasos.malphasos.report.application.model.ReportSheet.Grafica.ABAJO;
import static com.malphasos.malphasos.report.application.model.ReportSheet.Grafica.ARRIBA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.malphasos.malphasos.report.application.model.ReportSheet.Grafica;
import com.malphasos.malphasos.report.application.model.ReportSheet.Marca;
import com.malphasos.malphasos.report.application.model.ReportSheet.Punto;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.ErrorChart;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.ErrorChart.Lectura;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La gráfica de error con banda de tolerancia de la página 2. Son los casos de {@code grafica-de-error.spec.ts},
 * que la fijaban cuando la dibujaba el navegador: la geometría no cambió al pasar al servidor (2026-10-17).
 */
class ErrorChartTest {

    private static Lectura lectura(String error, Boolean conforme) {
        return new Lectura(new BigDecimal(error), conforme);
    }

    @Test
    @DisplayName("la banda es simétrica alrededor del cero y no toca los bordes")
    void bandaSimetrica() {
        Grafica grafica = ErrorChart.of(List.of(lectura("0.2", true)), new BigDecimal("0.5"));

        assertThat(grafica.ceroY() - grafica.banda().y())
                .isCloseTo(grafica.banda().y() + grafica.banda().alto() - grafica.ceroY(), within(1e-9));
        assertThat(grafica.banda().y()).isGreaterThan(ARRIBA);
        assertThat(grafica.banda().y() + grafica.banda().alto()).isLessThan(ABAJO);
    }

    @Test
    @DisplayName("una lectura fuera de tolerancia cae fuera de la banda, y una dentro, dentro")
    void fueraYDentro() {
        Grafica grafica = ErrorChart.of(List.of(lectura("0.9", false), lectura("-0.3", true)), new BigDecimal("0.5"));
        List<Punto> puntos = grafica.puntos();

        assertThat(puntos.get(0).y()).isLessThan(grafica.banda().y());
        assertThat(puntos.get(1).y()).isGreaterThan(grafica.banda().y())
                .isLessThan(grafica.banda().y() + grafica.banda().alto());
        assertThat(puntos).extracting(Punto::conforme).containsExactly(false, true);
        assertThat(puntos).extracting(Punto::fuera).containsExactly(true, false);
    }

    @Test
    @DisplayName("el borde de la tolerancia cae justo en el borde de la banda")
    void elBorde() {
        Grafica grafica = ErrorChart.of(List.of(lectura("0.5", true)), new BigDecimal("0.5"));

        assertThat(grafica.puntos().getFirst().y()).isCloseTo(grafica.banda().y(), within(1e-9));
    }

    @Test
    @DisplayName("las lecturas se reparten a lo ancho, en su orden")
    void aLoAncho() {
        Grafica grafica = ErrorChart.of(
                List.of(lectura("0.1", true), lectura("0.2", true), lectura("0.3", true), lectura("0.4", true)),
                BigDecimal.ONE);

        assertThat(grafica.puntos()).extracting(Punto::x).containsExactly(86.0, 162.0, 238.0, 314.0);
    }

    @Test
    @DisplayName("las marcas dicen la tolerancia con su signo, y el cero")
    void marcas() {
        assertThat(ErrorChart.of(List.of(), new BigDecimal("0.5")).marcas())
                .extracting(Marca::texto).containsExactly("+0,5", "0", "−0,5");
    }

    @Test
    @DisplayName("sin tolerancia no hay banda, y la escala la da el mayor error")
    void sinTolerancia() {
        Grafica grafica = ErrorChart.of(List.of(lectura("-2", null)), null);

        assertThat(grafica.banda()).isNull();
        assertThat(grafica.marcas()).extracting(Marca::texto).containsExactly("+2", "0", "−2");
        assertThat(grafica.puntos().getFirst().y()).isLessThan(ABAJO);
    }

    @Test
    @DisplayName("sin tolerancia, la marca del eje es el mayor error, con su signo y en coma decimal")
    void marcaDecimal() {
        // La etiqueta sale del error en decimal. Pasarla por double daría lo mismo con los cuatro decimales
        // que guarda la base —la mutación que lo prueba sobrevive, y es equivalente—, pero el número que se
        // escribe no tiene por qué haber pasado por coma flotante.
        assertThat(ErrorChart.of(List.of(lectura("0.3", null)), null).marcas())
                .extracting(Marca::texto).containsExactly("+0,3", "0", "−0,3");
    }

    @Test
    @DisplayName("sin tolerancia ni error, una sola marca: el cero")
    void soloCero() {
        assertThat(ErrorChart.of(List.of(lectura("0", null)), null).marcas())
                .extracting(Marca::texto).containsExactly("0");
    }
}
