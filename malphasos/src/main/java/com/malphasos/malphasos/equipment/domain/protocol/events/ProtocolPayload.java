package com.malphasos.malphasos.equipment.domain.protocol.events;

import com.malphasos.malphasos.shared.domain.events.Payload;

/** Datos de un protocolo que viajan con sus eventos. */
public record ProtocolPayload(String nombre, String descripcion) implements Payload {
}
