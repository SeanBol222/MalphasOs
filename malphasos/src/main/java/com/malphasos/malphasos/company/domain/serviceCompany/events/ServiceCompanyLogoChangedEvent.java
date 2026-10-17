package com.malphasos.malphasos.company.domain.serviceCompany.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se puso, se cambió o se quitó el logo de la empresa. */
public record ServiceCompanyLogoChangedEvent(EventMetadata metadata, ServiceCompanyPayload payload)
        implements DomainEvent<ServiceCompanyPayload> {

    public static final String TYPE = "service-company.logo-changed";
}
