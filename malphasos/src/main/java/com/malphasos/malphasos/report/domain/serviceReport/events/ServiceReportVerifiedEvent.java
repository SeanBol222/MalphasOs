package com.malphasos.malphasos.report.domain.serviceReport.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se registraron las lecturas de la verificacion metrologica de un reporte. */
public record ServiceReportVerifiedEvent(EventMetadata metadata, ServiceReportPayload payload)
        implements DomainEvent<ServiceReportPayload> {

    public static final String TYPE = "service-report.verified";
}
