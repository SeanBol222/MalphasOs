package com.malphasos.malphasos.workorder.infrastructure.input.model.request;

import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Programa un mantenimiento. La orden nace sin ingeniero y sin equipos: ambos se añaden después,
 * igual que en el formulario de la especificación.
 */
@Schema(name = "WorkOrderScheduleRequest")
public record WorkOrderScheduleRequest(
        @NotNull(message = "El cliente es obligatorio") UUID idCliente,

        @NotNull(message = "La sede es obligatoria") UUID idSede,

        @Schema(description = "Dia para el que se programa el servicio, no la fecha de registro")
        @NotNull(message = "La fecha de mantenimiento es obligatoria") LocalDate fechaMantenimiento,

        @NotNull(message = "La periodicidad es obligatoria") Periodicity periodicidad,

        @NotNull(message = "El tipo de servicio es obligatorio") ServiceType tipoServicio) {
}
