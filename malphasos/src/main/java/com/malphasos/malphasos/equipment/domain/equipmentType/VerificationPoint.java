package com.malphasos.malphasos.equipment.domain.equipmentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un valor en el que se mantiene constante el patrón o el equipo durante una verificación.
 *
 * <p>No es un agregado: no tiene vida fuera de la {@link TypeVerification} que lo declara, de modo que
 * se entra y se sale por {@link EquipmentType}. Una verificación se hace en varios puntos —a 50, a 100
 * y a 150 mmHg— y en cada uno se toman las lecturas que su verificación declara.
 *
 * <p><b>Ya no lleva unidad, y es la corrección del 2026-10-03.</b> La tenía, y eso obligaba a teclear
 * «mmHg» tantas veces como puntos hubiera, con el agujero de que dos puntos hermanos podían
 * contradecirse —50 mmHg y 100 kPa en la misma verificación—. La unidad la declara ahora la
 * verificación una sola vez y el punto la hereda, así que la contradicción dejó de ser expresable.
 *
 * <p><b>El valor admite negativos a propósito.</b> Un congelador se verifica a −20 °C, y exigir un
 * valor positivo habría dejado fuera media cadena de frío.
 *
 * <p>Se retira en vez de borrarse, como todo aquí: reconfigurar cómo se verifica un tipo no debe hacer
 * desaparecer el punto con el que se hicieron los reportes anteriores.
 */
public record VerificationPoint(UUID id, BigDecimal valor, boolean estadoActivo) {

    /** Cuántos decimales guarda la columna. Más allá, dos puntos distintos serían el mismo en la base. */
    private static final int DECIMALES = 4;

    public VerificationPoint {
        if (valor == null) {
            throw new IllegalArgumentException("Un punto de verificacion necesita su valor");
        }
    }

    /** Un punto nuevo, activo. El valor se normaliza a la escala de la columna. */
    public static VerificationPoint of(BigDecimal valor) {
        return new VerificationPoint(UUID.randomUUID(), normalizar(valor), true);
    }

    /** Un punto que vuelve de la base, con su estado tal como está guardado. */
    public static VerificationPoint rehydrate(UUID id, BigDecimal valor, boolean estadoActivo) {
        return new VerificationPoint(id, normalizar(valor), estadoActivo);
    }

    /** El mismo punto, retirado. */
    public VerificationPoint deactivated() {
        return new VerificationPoint(id, valor, false);
    }

    /**
     * Si este punto mide en el mismo valor que el otro.
     *
     * <p>Compara por valor <b>numérico</b> y no por texto: {@code 100} y {@code 100.0000} son el mismo
     * punto, y {@code equals} de {@code BigDecimal} diría que no. Es lo que impide declarar dos veces el
     * mismo punto escribiéndolo distinto.
     *
     * <p>Ya no compara unidades: dos puntos que se comparan son siempre de la misma verificación, y la
     * unidad es de la verificación.
     */
    public boolean mideLoMismoQue(VerificationPoint otro) {
        return valor.compareTo(otro.valor) == 0;
    }

    private static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(DECIMALES, java.math.RoundingMode.HALF_UP);
    }
}
