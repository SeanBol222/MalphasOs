package com.malphasos.malphasos.company.domain.serviceCompany.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se corrigieron los datos de la empresa o los textos de sus documentos. */
public record ServiceCompanyUpdatedEvent(EventMetadata metadata, ServiceCompanyPayload payload)
        implements DomainEvent<ServiceCompanyPayload> {

    public static final String TYPE = "service-company.updated";
}
