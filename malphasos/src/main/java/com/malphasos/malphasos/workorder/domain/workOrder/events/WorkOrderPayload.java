package com.malphasos.malphasos.workorder.domain.workOrder.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos de una orden que viajan con sus eventos.
 *
 * <p>No lleva la lista de equipos: cambia por su cuenta y tiene eventos propios. Quien escuche un
 * cambio de estado no necesita el inventario entero para saber qué pasó.
 */
public record WorkOrderPayload(
        UUID idCliente,
        UUID idSede,
        LocalDate fechaMantenimiento,
        Periodicity periodicidad,
        ServiceType tipoServicio,
        ExecutionState estadoEjecucion,
        UUID idIngeniero)
        implements Payload {
}
