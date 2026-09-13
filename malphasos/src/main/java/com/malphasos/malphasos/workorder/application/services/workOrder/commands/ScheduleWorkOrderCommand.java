package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.time.LocalDate;
import java.util.UUID;

/** Programa un mantenimiento. La orden nace sin ingeniero y sin equipos. */
public record ScheduleWorkOrderCommand(
        UUID idCliente,
        UUID idSede,
        LocalDate fechaMantenimiento,
        Periodicity periodicidad,
        ServiceType tipoServicio) {
}
