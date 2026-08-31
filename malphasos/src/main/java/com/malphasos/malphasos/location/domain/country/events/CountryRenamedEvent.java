package com.malphasos.malphasos.location.domain.country.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/**
 * Un país pasó a llamarse de otra forma.
 *
 * <p>El original tenía un único {@code CountryUpdatedEvent} emitido desde dos métodos casi
 * idénticos, {@code updateCountry} y {@code updateCountryPatch}, con la particularidad de que el
 * segundo escribía {@code "country.patch"} en la metadata mientras construía un evento que decía
 * "updated". Un consumidor que filtrara por el tipo del evento y otro que filtrara por la clase
 * habrían visto cosas distintas. Aquí el nombre del hecho es uno solo, porque el hecho es uno solo:
 * si la petición trae los campos completos o solo algunos es asunto del API, no del dominio.
 */
public record CountryRenamedEvent(EventMetadata metadata, CountryPayload payload)
        implements DomainEvent<CountryPayload> {

    public static final String TYPE = "country.renamed";
}
