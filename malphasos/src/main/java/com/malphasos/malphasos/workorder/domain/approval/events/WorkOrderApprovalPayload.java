package com.malphasos.malphasos.workorder.domain.approval.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/** Qué orden, quién la aprobó y si lo hizo desde su propia cuenta. */
public record WorkOrderApprovalPayload(UUID idOrdenTrabajo, UUID idSupervisor, boolean desdeSuCuenta)
        implements Payload {
}
