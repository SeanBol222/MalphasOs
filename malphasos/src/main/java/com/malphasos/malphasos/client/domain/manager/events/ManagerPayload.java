package com.malphasos.malphasos.client.domain.manager.events;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/**
 * Datos de un encargado que viajan con sus eventos.
 *
 * <p>{@code idAsignacion} es la sede o el área de la que se encarga, según diga {@code tipo}. Nunca
 * las dos.
 */
public record ManagerPayload(ManagerType tipo, UUID idAsignacion) implements Payload {
}
