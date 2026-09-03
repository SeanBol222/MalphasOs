package com.malphasos.malphasos.equipment.domain.brand.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Una marca pasó a llamarse de otra forma. */
public record BrandRenamedEvent(EventMetadata metadata, BrandPayload payload) implements DomainEvent<BrandPayload> {

    public static final String TYPE = "brand.renamed";
}
