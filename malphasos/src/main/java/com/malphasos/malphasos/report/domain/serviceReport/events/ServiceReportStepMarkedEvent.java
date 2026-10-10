package com.malphasos.malphasos.report.domain.serviceReport.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** Un paso del protocolo de un reporte se marcó: hecho, no aplica, o de vuelta a pendiente. */
public record ServiceReportStepMarkedEvent(EventMetadata metadata, ServiceReportPayload payload)
        implements DomainEvent<ServiceReportPayload> {

    public static final String TYPE = "service-report.step-marked";
}
