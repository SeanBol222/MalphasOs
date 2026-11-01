package com.malphasos.malphasos.workorder.domain.approval.events;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;

/** El supervisor del cliente aprobó la orden: se puede facturar. */
public record WorkOrderApprovedEvent(EventMetadata metadata, WorkOrderApprovalPayload payload)
        implements DomainEvent<WorkOrderApprovalPayload> {

    public static final String TYPE = "work-order.approved";
}
