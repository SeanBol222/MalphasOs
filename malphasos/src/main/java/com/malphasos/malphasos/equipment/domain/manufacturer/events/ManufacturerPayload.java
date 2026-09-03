package com.malphasos.malphasos.equipment.domain.manufacturer.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Datos de un fabricante que viajan con sus eventos. */
public record ManufacturerPayload(String nombre, UUID idPais) implements Payload {
}
