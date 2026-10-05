package com.malphasos.malphasos.equipment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * La ficha técnica de un modelo: lo que dice su placa y su registro sanitario.
 *
 * <p>Es un valor y no siete campos sueltos del modelo porque se lee y se corrige junta —quien la
 * llena tiene la placa del equipo delante— y porque así la validación vive en un sitio. Todo es
 * opcional: un modelo existe antes de que alguien lea su placa, y la hoja de vida imprime «—» en lo
 * que falte.
 *
 * <p><b>Voltaje y amperaje vivían en el tipo de equipo</b> hasta el 2026-10-05, heredados del
 * original. Son del modelo —dos balanzas de marcas distintas no consumen lo mismo— y bajaron aquí con
 * {@code V15}, junto con los tres datos eléctricos que faltaban.
 *
 * @param riesgo          clasificación por riesgo del dispositivo médico
 * @param caracteristicas lo que distingue a este modelo de los otros de su tipo, en texto libre
 * @param alimentacion    de dónde toma la energía: «Red eléctrica», «Baterías»…
 * @param voltaje         voltaje nominal, en V
 * @param potencia        potencia nominal, en W
 * @param amperaje        corriente nominal, en A
 * @param frecuencia      frecuencia de la red, en Hz
 */
public record TechnicalSheet(
        RiskClass riesgo,
        String caracteristicas,
        String alimentacion,
        Integer voltaje,
        Integer potencia,
        BigDecimal amperaje,
        Integer frecuencia) {

    /** Un modelo del que todavía no se sabe nada más que su nombre. */
    public static final TechnicalSheet EMPTY = new TechnicalSheet(null, null, null, null, null, null, null);

    /**
     * Valida y normaliza: un texto en blanco es lo mismo que no tenerlo, y los números son positivos.
     *
     * <p>No es el constructor canónico a propósito: {@code rehydrate} tiene que poder cargar una fila
     * vieja aunque ya no cumpla una regla nueva, y por eso valida quien crea o corrige, no quien lee.
     */
    public static TechnicalSheet of(
            RiskClass riesgo,
            String caracteristicas,
            String alimentacion,
            Integer voltaje,
            Integer potencia,
            BigDecimal amperaje,
            Integer frecuencia) {

        return new TechnicalSheet(
                riesgo,
                texto(caracteristicas),
                texto(alimentacion),
                positivo(voltaje, "voltaje"),
                positivo(potencia, "potencia"),
                positivo(amperaje),
                positivo(frecuencia, "frecuencia"));
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static Integer positivo(Integer valor, String campo) {
        if (valor != null && valor <= 0) {
            throw new IllegalArgumentException("El " + campo + " es positivo, y se recibio " + valor);
        }

        return valor;
    }

    /**
     * Positivo y con dos decimales, que son los que guarda la columna. Normalizar la escala no es
     * cosmético: {@code BigDecimal.equals} distingue 2.5 de 2.50, y sin esto volver a mandar la misma
     * ficha contaría como un cambio y emitiría un evento por nada.
     */
    private static BigDecimal positivo(BigDecimal amperaje) {
        if (amperaje == null) {
            return null;
        }
        if (amperaje.signum() <= 0) {
            throw new IllegalArgumentException("El amperaje es positivo, y se recibio " + amperaje);
        }
        try {
            return amperaje.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException demasiadosDecimales) {
            throw new IllegalArgumentException(
                    "El amperaje admite dos decimales, y se recibio " + amperaje.toPlainString());
        }
    }
}
