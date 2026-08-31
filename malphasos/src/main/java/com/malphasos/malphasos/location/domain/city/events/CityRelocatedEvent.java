package com.malphasos.malphasos.location.domain.city.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/**
 * Una ciudad pasó a pertenecer a otro país.
 *
 * <p>Es un hecho distinto de un cambio de nombre y merece su propio evento: renombrar una ciudad no
 * afecta a nadie más, pero moverla de país cambia la cobertura geográfica de todas las sedes que
 * hay en ella. El original emitía un único {@code CityUpdatedEvent} para ambos casos, de modo que
 * un consumidor interesado solo en lo segundo tenía que comparar el payload contra el estado
 * anterior para averiguar qué había cambiado.
 */
public record CityRelocatedEvent(EventMetadata metadata, CityPayload payload)
        implements DomainEvent<CityPayload> {

    public static final String TYPE = "city.relocated";
}
