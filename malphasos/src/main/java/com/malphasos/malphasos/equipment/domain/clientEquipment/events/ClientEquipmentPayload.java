package com.malphasos.malphasos.equipment.domain.clientEquipment.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Datos de una unidad de un cliente que viajan con sus eventos. */
public record ClientEquipmentPayload(String serie, UUID idModelo, UUID idAreaServicio)
        implements Payload {
}
