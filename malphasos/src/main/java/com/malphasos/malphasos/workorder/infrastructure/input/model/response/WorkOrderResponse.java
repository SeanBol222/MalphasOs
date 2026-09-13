package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Una orden de trabajo con su alcance. */
@Builder
@Schema(name = "WorkOrderResponse")
public record WorkOrderResponse(
        UUID id,
        UUID idCliente,
        UUID idSede,
        LocalDate fechaMantenimiento,
        Periodicity periodicidad,
        ServiceType tipoServicio,
        ExecutionState estadoEjecucion,
        @Schema(description = "Nulo mientras la orden esta creada pero sin asignar")
        UUID idIngeniero,
        List<WorkOrderEquipmentResponse> equipos,
        boolean estadoActivo) {
}
