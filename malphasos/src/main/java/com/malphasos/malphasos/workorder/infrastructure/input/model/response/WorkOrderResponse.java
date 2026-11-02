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
        @Schema(description = "Los seis primeros caracteres del identificador, en mayusculas", example = "4F2A9C")
        String numero,
        UUID idCliente,
        UUID idSede,
        LocalDate fechaMantenimiento,
        Periodicity periodicidad,
        ServiceType tipoServicio,
        @Schema(description = "Un correctivo en garantia") boolean garantia,
        ExecutionState estadoEjecucion,
        @Schema(description = "Nulo mientras la orden esta creada pero sin asignar")
        UUID idIngeniero,
        @Schema(description = "El nombre del ingeniero, para que quien lee la orden lo nombre sin leer "
                + "personas (2026-11-02); nulo sin ingeniero")
        String nombreIngeniero,
        List<WorkOrderEquipmentResponse> equipos,
        boolean estadoActivo,
        @Schema(description = "Lo que la orden era al ejecutarse (V49). Nulo mientras no se ha ejecutado")
        WorkOrderDocumentResponse documento,
        @Schema(description = "El documento se compuso despues de ejecutarla, con los datos de ese dia")
        boolean documentoTardio) {
}
