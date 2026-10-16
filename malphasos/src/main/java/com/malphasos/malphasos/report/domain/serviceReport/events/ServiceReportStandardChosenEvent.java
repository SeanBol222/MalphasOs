package com.malphasos.malphasos.report.domain.serviceReport.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Se eligio, o se cambio, el patron contra el que se toman las lecturas de un reporte (V27). */
public record ServiceReportStandardChosenEvent(EventMetadata metadata, ServiceReportPayload payload)
        implements DomainEvent<ServiceReportPayload> {

    public static final String TYPE = "service-report.standard-chosen";
}
