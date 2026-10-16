package com.malphasos.malphasos.report.domain.serviceReport.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/**
 * Se puso un patron en una verificacion de un reporte, o se refresco su copia (V27; por verificacion desde
 * V30).
 */
public record ServiceReportStandardChosenEvent(EventMetadata metadata, ServiceReportPayload payload)
        implements DomainEvent<ServiceReportPayload> {

    public static final String TYPE = "service-report.standard-chosen";
}
