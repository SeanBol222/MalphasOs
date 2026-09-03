package com.malphasos.malphasos.equipment.domain.equipment.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** La asociación que un evento de equipo transporta: qué marca fabrica qué tipo. */
public record EquipmentPayload(UUID idTipoEquipo, UUID idMarca) implements Payload {
}
