package com.malphasos.malphasos.client.domain.serviceArea.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Datos de un área de servicio que viajan con sus eventos. */
public record ServiceAreaPayload(String nombre, UUID idSede) implements Payload {
}
