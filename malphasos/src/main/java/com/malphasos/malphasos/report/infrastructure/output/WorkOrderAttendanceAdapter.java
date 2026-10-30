package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.WorkOrderAttendancePort;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contesta a {@code equipment} si una unidad está en una orden sin ejecutar (V50): si tiene un reporte
 * activo en ella, que es lo que pone un equipo en una orden.
 *
 * <p>Vive aquí porque los reportes son de este módulo, que ya dependía de {@code equipment}. Y pregunta a
 * su propia persistencia, no al servicio de órdenes: ese depende de {@code equipment}, y el traslado que
 * pregunta esto es de {@code equipment}, de modo que el ciclo entre beans no arrancaría.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderAttendanceAdapter implements WorkOrderAttendancePort {

    private final ServiceReportPersistencePort serviceReportPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public boolean inUnexecutedWorkOrder(UUID idEquipoCliente) {
        return serviceReportPersistencePort.inUnexecutedWorkOrder(idEquipoCliente);
    }
}
