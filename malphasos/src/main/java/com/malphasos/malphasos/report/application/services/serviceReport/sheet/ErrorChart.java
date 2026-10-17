package com.malphasos.malphasos.report.application.services.serviceReport.sheet;

import static com.malphasos.malphasos.report.application.model.ReportSheet.Grafica.ABAJO;
import static com.malphasos.malphasos.report.application.model.ReportSheet.Grafica.ARRIBA;
import static com.malphasos.malphasos.report.application.model.ReportSheet.Grafica.DERECHA;
import static com.malphasos.malphasos.report.application.model.ReportSheet.Grafica.IZQUIERDA;

import com.malphasos.malphasos.report.application.model.ReportSheet.Banda;
import com.malphasos.malphasos.report.application.model.ReportSheet.Grafica;
import com.malphasos.malphasos.report.application.model.ReportSheet.Marca;
import com.malphasos.malphasos.report.application.model.ReportSheet.Punto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * La gráfica de la página 2 (2026-10-16): el error de cada lectura —equipo menos patrón— frente a la banda
 * de tolerancia. Es la que eligió el usuario, y la que dibujaba el navegador hasta el 2026-10-17, con la
 * misma geometría: se ve de un vistazo qué lectura se salió y por cuánto.
 */
public final class ErrorChart {

    private ErrorChart() {
    }

    /** Un error y si es conforme; nulo si la lectura no tiene tolerancia. */
    public record Lectura(BigDecimal error, Boolean conforme) {
    }

    /**
     * La escala es simétrica alrededor de cero y deja aire: llega a la mayor de 1,6 veces la tolerancia o
     * 1,25 veces el mayor error, de modo que la banda nunca toca el borde y un punto fuera de ella se ve
     * fuera. Las lecturas se reparten a lo ancho en el orden en que llegan.
     */
    public static Grafica of(List<Lectura> lecturas, BigDecimal tolerancia) {
        // En decimal y no en double: es también la etiqueta del eje cuando no hay tolerancia.
        BigDecimal mayor = lecturas.stream()
                .map(lectura -> lectura.error() == null ? BigDecimal.ZERO : lectura.error().abs())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        double mayorError = mayor.doubleValue();
        double tol = tolerancia == null ? 0 : tolerancia.doubleValue();
        double tope = Math.max(tol * 1.6, mayorError * 1.25);
        double escala = tope == 0 ? 1 : tope;
        DoubleUnaryOperator y = v -> ARRIBA + ((escala - v) / (2 * escala)) * (ABAJO - ARRIBA);
        double paso = (double) (DERECHA - IZQUIERDA) / Math.max(lecturas.size(), 1);

        List<Marca> marcas = new ArrayList<>();
        BigDecimal limite = tolerancia != null ? tolerancia : mayor;
        for (BigDecimal valor : List.of(limite, BigDecimal.ZERO, limite.negate())) {
            String texto = SheetFormat.conSigno(valor);
            // Sin tolerancia y sin error, las tres marcas serían el mismo cero.
            if (marcas.stream().noneMatch(marca -> marca.texto().equals(texto))) {
                marcas.add(new Marca(y.applyAsDouble(valor.doubleValue()), texto));
            }
        }

        List<Punto> puntos = new ArrayList<>();
        for (int i = 0; i < lecturas.size(); i++) {
            Lectura lectura = lecturas.get(i);
            puntos.add(new Punto(IZQUIERDA + (i + 0.5) * paso, y.applyAsDouble(valor(lectura.error())),
                    lectura.conforme()));
        }

        Banda banda = tolerancia == null
                ? null
                : new Banda(y.applyAsDouble(tol), y.applyAsDouble(-tol) - y.applyAsDouble(tol));

        return new Grafica(banda, y.applyAsDouble(0), puntos, marcas);
    }

    private static double valor(BigDecimal numero) {
        return numero == null ? 0 : numero.doubleValue();
    }
}
