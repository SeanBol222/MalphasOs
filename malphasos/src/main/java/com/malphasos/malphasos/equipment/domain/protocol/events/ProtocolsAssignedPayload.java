package com.malphasos.malphasos.equipment.domain.protocol.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import java.util.UUID;

/** Los protocolos de un tipo, en el orden de su prioridad. */
public record ProtocolsAssignedPayload(UUID idTipoEquipo, List<UUID> protocolos) implements Payload {
}
