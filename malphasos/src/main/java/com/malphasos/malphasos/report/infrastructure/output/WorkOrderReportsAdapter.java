package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contesta a {@code work-order} qué equipos de una orden tienen su reporte cerrado (2026-10-14).
 *
 * <p>Vive aquí y no allí porque los reportes son de este módulo, y este ya dependía del de órdenes:
 * que fuera al revés cerraría un ciclo. Un reporte retirado no cuenta, aunque estuviera cerrado: si se
 * retiró, el equipo vuelve a estar sin reporte, que es lo mismo que dice la ficha de la orden.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderReportsAdapter implements WorkOrderReportsPort {

    private final ServiceReportPersistencePort serviceReportPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> equipmentWithFinishedReport(UUID idOrdenTrabajo) {
        return serviceReportPersistencePort.findByWorkOrder(idOrdenTrabajo).stream()
                .filter(ServiceReport::isEstadoActivo)
                .filter(reporte -> reporte.getEstado() == ReportState.FINALIZADO)
                .map(ServiceReport::getIdEquipoCliente)
                .collect(Collectors.toUnmodifiableSet());
    }
}
