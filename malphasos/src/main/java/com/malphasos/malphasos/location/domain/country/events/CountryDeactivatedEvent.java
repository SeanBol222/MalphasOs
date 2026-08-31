package com.malphasos.malphasos.location.domain.country.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/**
 * Un país dejó de estar disponible.
 *
 * <p>El original lo llamaba {@code CountryDeletedEvent}, pero aquí nada se borra: el registro
 * permanece con {@code b_estado_activo} en falso para conservar el historial. Un consumidor que lea
 * "deleted" puede concluir razonablemente que la fila ya no existe, y eso no es cierto.
 */
public record CountryDeactivatedEvent(EventMetadata metadata, CountryPayload payload)
        implements DomainEvent<CountryPayload> {

    public static final String TYPE = "country.deactivated";
}
