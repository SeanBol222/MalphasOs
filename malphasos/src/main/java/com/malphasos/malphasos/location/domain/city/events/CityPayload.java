package com.malphasos.malphasos.location.domain.city.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Datos de una ciudad que viajan con sus eventos. El identificador va en la metadata. */
public record CityPayload(String nombre, UUID idPais) implements Payload {
}
