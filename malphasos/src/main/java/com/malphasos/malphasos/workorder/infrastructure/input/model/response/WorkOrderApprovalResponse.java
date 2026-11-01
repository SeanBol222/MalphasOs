package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "WorkOrderApprovalResponse")
public record WorkOrderApprovalResponse(
        UUID id,
        UUID idOrdenTrabajo,
        UUID idSupervisor,
        @Schema(description = "Quien tenia la sesion; nulo si era el SuperUsuario") UUID idOperador,
        @Schema(description = "Si la aprobo el supervisor en su propia sesion; si no, en sitio")
        boolean desdeSuCuenta,
        LocalDateTime aprobada) {

    public static WorkOrderApprovalResponse from(WorkOrderApproval aprobacion) {
        return new WorkOrderApprovalResponse(
                aprobacion.getId(),
                aprobacion.getIdOrdenTrabajo(),
                aprobacion.getIdSupervisor(),
                aprobacion.getIdOperador(),
                aprobacion.fromOwnAccount(),
                aprobacion.getAprobada());
    }
}
