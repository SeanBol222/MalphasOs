package com.malphasos.malphasos.report.domain.serviceReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Una lectura tomada durante la verificación metrológica: qué marcaba el patrón y qué marcaba el
 * equipo.
 *
 * <p>No es un agregado: no tiene vida fuera del reporte que la registra, de modo que se entra y se
 * sale por {@link ServiceReport}. Es la contraparte de {@code VerificationPoint}, que vive en el tipo
 * de equipo: aquél declara <b>dónde</b> se mide y <b>cuántas veces</b>, y ésta guarda <b>lo que
 * salió</b>.
 *
 * <p><b>El punto es opcional a propósito.</b> Con la modalidad {@code PATRON_EQUIPO_VARIABLE} el tipo
 * de equipo no declara puntos —lo impone un {@code CHECK} desde V8— y las lecturas existen igual:
 * patrón y equipo se recorren juntos y el ingeniero decide cuántas tomar. Un punto nulo significa
 * «esta lectura no corresponde a ningún valor constante declarado», no «falta el dato».
 *
 * <p><b>Se guardan las dos lecturas, incluso la del lado que debería ser constante.</b> Lo constante
 * lo es por cómo se monta el ensayo, no por decreto: si el patrón marcó 50,2 donde el punto dice 50,
 * el reporte tiene que decir 50,2. Y la unidad viaja con la lectura, no se consulta del punto, porque
 * el punto se retira cuando el tipo se reconfigura y un reporte viejo tiene que seguir imprimiéndose
 * igual.
 */
public record VerificationReading(
        UUID id,
        UUID idPuntoVerificacion,
        int secuencia,
        BigDecimal valorPatron,
        BigDecimal valorEquipo,
        String unidad,
        boolean estadoActivo) {

    /** Cuántos decimales guarda la columna. Más allá, dos lecturas distintas serían la misma en la base. */
    private static final int DECIMALES = 4;

    /** El tope de lecturas por punto que declara {@code tipo_equipo.i_cantidad_datos} desde V8. */
    public static final int MAXIMA_SECUENCIA = 100;

    public VerificationReading {
        if (secuencia < 1 || secuencia > MAXIMA_SECUENCIA) {
            throw new IllegalArgumentException(
                    "La lectura va numerada entre 1 y " + MAXIMA_SECUENCIA + ", y se recibio " + secuencia);
        }
        if (valorPatron == null || valorEquipo == null) {
            throw new IllegalArgumentException(
                    "Una lectura necesita el valor del patron y el del equipo: el reporte imprime los dos");
        }
        if (unidad == null || unidad.isBlank()) {
            throw new IllegalArgumentException(
                    "Una lectura necesita su unidad: un numero sin unidad no se puede imprimir");
        }
    }

    /** Una lectura nueva, activa. Los valores se normalizan a la escala de la columna. */
    public static VerificationReading of(
            UUID idPuntoVerificacion,
            int secuencia,
            BigDecimal valorPatron,
            BigDecimal valorEquipo,
            String unidad) {

        return new VerificationReading(
                UUID.randomUUID(),
                idPuntoVerificacion,
                secuencia,
                normalizar(valorPatron),
                normalizar(valorEquipo),
                unidad == null ? null : unidad.trim(),
                true);
    }

    /** Una lectura que vuelve de la base, con su estado tal como está guardado. */
    public static VerificationReading rehydrate(
            UUID id,
            UUID idPuntoVerificacion,
            int secuencia,
            BigDecimal valorPatron,
            BigDecimal valorEquipo,
            String unidad,
            boolean estadoActivo) {

        return new VerificationReading(
                id,
                idPuntoVerificacion,
                secuencia,
                normalizar(valorPatron),
                normalizar(valorEquipo),
                unidad,
                estadoActivo);
    }

    /** La misma lectura, retirada. */
    public VerificationReading deactivated() {
        return new VerificationReading(
                id, idPuntoVerificacion, secuencia, valorPatron, valorEquipo, unidad, false);
    }

    /**
     * Si las dos ocupan el mismo sitio en la verificación: el mismo punto y el mismo número.
     *
     * <p>Es la identidad de una lectura dentro de su reporte, y es lo mismo que fija el índice único
     * de la tabla —con {@code NULLS NOT DISTINCT}, por eso aquí dos puntos nulos también cuentan como
     * el mismo sitio—. No compara valores: dos lecturas del mismo punto con el mismo número no son dos
     * medidas, son la misma medida escrita dos veces.
     */
    public boolean ocupaElMismoSitioQue(VerificationReading otra) {
        return secuencia == otra.secuencia
                && java.util.Objects.equals(idPuntoVerificacion, otra.idPuntoVerificacion);
    }

    /**
     * Si las dos dicen exactamente lo mismo.
     *
     * <p>Es lo que permite que registrar dos veces la misma verificación no cuente como un cambio.
     *
     * <p>Compara los valores por magnitud <b>numérica</b> y no con {@code equals}, que en
     * {@code BigDecimal} diría que {@code 50} y {@code 50.0000} son distintos. <b>Hoy esa precaución
     * no cambia ningún resultado y conviene saberlo</b>: tanto {@link #of} como {@link #rehydrate}
     * normalizan a la escala de la columna, de modo que dos lecturas iguales llegan aquí con la misma
     * escala. Se comprobó sustituyendo {@code compareTo} por {@code equals} y la batería siguió verde.
     * Se conserva porque es lo correcto si algún día se deja de normalizar, no porque haya una prueba
     * que lo exija.
     */
    public boolean diceLoMismoQue(VerificationReading otra) {
        return ocupaElMismoSitioQue(otra)
                && unidad.equalsIgnoreCase(otra.unidad)
                && valorPatron.compareTo(otra.valorPatron) == 0
                && valorEquipo.compareTo(otra.valorEquipo) == 0;
    }

    private static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(DECIMALES, RoundingMode.HALF_UP);
    }
}
