package com.malphasos.malphasos.report.domain.serviceReport.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se abrio el reporte de un equipo de una orden de trabajo. */
public record ServiceReportOpenedEvent(EventMetadata metadata, ServiceReportPayload payload)
        implements DomainEvent<ServiceReportPayload> {

    public static final String TYPE = "service-report.opened";
}
