package com.malphasos.malphasos.equipment.domain.equipmentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un valor en el que se mantiene constante el patrón o el equipo durante la verificación.
 *
 * <p>No es un agregado: no tiene vida fuera del tipo de equipo que lo declara, de modo que se entra y
 * se sale por {@link EquipmentType}. Una verificación se hace en varios puntos —a 50, a 100 y a 150
 * mmHg— y en cada uno se toman las lecturas que el tipo declara.
 *
 * <p><b>El valor admite negativos a propósito.</b> Un congelador se verifica a −20 °C, y exigir un
 * valor positivo habría dejado fuera media cadena de frío. Lo único que no se admite es una unidad en
 * blanco: un número sin unidad no se puede escribir en un reporte.
 *
 * <p>Se retira en vez de borrarse, como todo aquí: reconfigurar cómo se verifica un tipo no debe hacer
 * desaparecer el punto con el que se hicieron los reportes anteriores.
 */
public record VerificationPoint(UUID id, BigDecimal valor, String unidad, boolean estadoActivo) {

    /** Cuántos decimales guarda la columna. Más allá, dos puntos distintos serían el mismo en la base. */
    private static final int DECIMALES = 4;

    public VerificationPoint {
        if (valor == null) {
            throw new IllegalArgumentException("Un punto de verificacion necesita su valor");
        }
        if (unidad == null || unidad.isBlank()) {
            throw new IllegalArgumentException(
                    "Un punto de verificacion necesita su unidad: un numero sin unidad no dice nada");
        }
    }

    /** Un punto nuevo, activo. El valor se normaliza a la escala de la columna. */
    public static VerificationPoint of(BigDecimal valor, String unidad) {
        return new VerificationPoint(
                UUID.randomUUID(), normalizar(valor), unidad == null ? null : unidad.trim(), true);
    }

    /** Un punto que vuelve de la base, con su estado tal como está guardado. */
    public static VerificationPoint rehydrate(
            UUID id, BigDecimal valor, String unidad, boolean estadoActivo) {

        return new VerificationPoint(id, normalizar(valor), unidad, estadoActivo);
    }

    /** El mismo punto, retirado. */
    public VerificationPoint deactivated() {
        return new VerificationPoint(id, valor, unidad, false);
    }

    /**
     * Si este punto mide lo mismo que el otro.
     *
     * <p>Compara por valor <b>numérico</b> y no por texto: {@code 100} y {@code 100.0000} son el mismo
     * punto, y {@code equals} de {@code BigDecimal} diría que no. Es lo que impide declarar dos veces el
     * mismo punto escribiéndolo distinto.
     */
    public boolean mideLoMismoQue(VerificationPoint otro) {
        return unidad.equalsIgnoreCase(otro.unidad) && valor.compareTo(otro.valor) == 0;
    }

    private static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(DECIMALES, java.math.RoundingMode.HALF_UP);
    }
}
