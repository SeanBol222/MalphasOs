package com.malphasos.malphasos.workorder.domain.approval.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** La aprobación dejó de valer: lo que se aprobó ya no es lo mismo. */
public record WorkOrderApprovalWithdrawnEvent(EventMetadata metadata, WorkOrderApprovalPayload payload)
        implements DomainEvent<WorkOrderApprovalPayload> {

    public static final String TYPE = "work-order.approval-withdrawn";
}
