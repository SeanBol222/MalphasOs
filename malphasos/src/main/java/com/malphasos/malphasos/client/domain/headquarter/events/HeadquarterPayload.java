package com.malphasos.malphasos.client.domain.headquarter.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Datos de una sede que viajan con sus eventos. */
public record HeadquarterPayload(String nombre, UUID idCliente, UUID idCiudad) implements Payload {
}
