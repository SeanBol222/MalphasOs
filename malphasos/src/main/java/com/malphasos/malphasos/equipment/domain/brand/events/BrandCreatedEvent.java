package com.malphasos.malphasos.equipment.domain.brand.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registró una marca nueva. */
public record BrandCreatedEvent(EventMetadata metadata, BrandPayload payload) implements DomainEvent<BrandPayload> {

    public static final String TYPE = "brand.created";
}
