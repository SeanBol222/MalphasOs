package com.malphasos.malphasos.shared.domain.events;

/**
 * Los datos que un evento de dominio lleva consigo.
 *
 * <p>Interfaz marcadora: cada agregado define su propio payload como un {@code record} con los
 * campos que sus consumidores necesitan. No es el agregado entero — un evento describe lo que pasó,
 * no el estado completo de quien lo produjo.
 *
 * <p>A diferencia del original, no extiende {@code Serializable}. La serialización de Java no se usa
 * en ningún punto del recorrido: el despacho in-process pasa el objeto por referencia y el de
 * mensajería lo convierte a JSON con Jackson. Exigirla obligaba a cada payload nuevo a arrastrar una
 * interfaz que nunca entra en juego.
 */
public interface Payload {
}
