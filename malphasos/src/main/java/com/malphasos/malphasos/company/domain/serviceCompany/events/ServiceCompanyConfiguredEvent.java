package com.malphasos.malphasos.company.domain.serviceCompany.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se configuró la empresa por primera vez, desde la pantalla o desde el archivo de arranque. */
public record ServiceCompanyConfiguredEvent(EventMetadata metadata, ServiceCompanyPayload payload)
        implements DomainEvent<ServiceCompanyPayload> {

    public static final String TYPE = "service-company.configured";
}
