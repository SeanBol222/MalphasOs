package com.malphasos.malphasos.equipment.domain.model.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Datos de un modelo que viajan con sus eventos. */
public record ModelPayload(String invima, UUID idFabricante, UUID idEquipo) implements Payload {
}
