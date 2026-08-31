package com.malphasos.malphasos.location.domain.country.events;

import com.malphasos.malphasos.shared.domain.events.Payload;

/**
 * Datos de un país que viajan con sus eventos.
 *
 * <p>No lleva el identificador: ya va en la metadata del evento, como {@code aggregateId}.
 */
public record CountryPayload(String codigoIso, String nombre) implements Payload {
}
