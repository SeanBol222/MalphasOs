package com.malphasos.malphasos.workorder.domain.workOrder;

/**
 * Cada cuánto se repite el mantenimiento de los equipos de una orden.
 *
 * <p>Los cuatro valores salen del esquema del sistema original, que los fijaba en un {@code CHECK}
 * sobre {@code n_periodicidad}. Se conservan en español, como el resto del vocabulario propio del
 * dominio: aquel esquema los tradujo después a inglés y convirtió <i>semestral</i> en
 * {@code BIANNUAL}, palabra que significa a la vez «dos veces al año» y «cada dos años».
 */
public enum Periodicity {
    MENSUAL,
    TRIMESTRAL,
    SEMESTRAL,
    ANUAL
}
