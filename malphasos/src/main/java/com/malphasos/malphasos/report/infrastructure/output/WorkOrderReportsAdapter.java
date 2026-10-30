package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.report.application.model.ActiveReportSummary;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.ServiceReportDrafts;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contesta a {@code work-order} por los reportes de sus órdenes, que desde {@code V50} son sus equipos.
 *
 * <p>Vive aquí y no allí porque los reportes son de este módulo, y este ya dependía del de órdenes: que
 * fuera al revés cerraría un ciclo. Un reporte retirado no cuenta: si se retiró, su equipo salió de la
 * orden.
 *
 * <p><b>No usa el servicio de reportes</b>, que depende del de órdenes: el de órdenes depende de este
 * adaptador, y el ciclo entre beans no arrancaría. Abre los borradores con {@link ServiceReportDrafts},
 * que tampoco depende de él.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderReportsAdapter implements WorkOrderReportsPort {

    private final ServiceReportPersistencePort serviceReportPersistencePort;
    private final ServiceReportDrafts serviceReportDrafts;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<ActiveReport> activeReports(UUID idOrdenTrabajo) {
        return activeReports(List.of(idOrdenTrabajo)).getOrDefault(idOrdenTrabajo, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ActiveReport>> activeReports(Collection<UUID> idsOrdenes) {
        return serviceReportPersistencePort.findActiveSummaries(idsOrdenes).stream()
                .collect(Collectors.groupingBy(
                        ActiveReportSummary::idOrdenTrabajo,
                        Collectors.mapping(WorkOrderReportsAdapter::comoActivo, Collectors.toList())));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> workOrdersOf(UUID idEquipoCliente) {
        return serviceReportPersistencePort.findByEquipment(idEquipoCliente).stream()
                .filter(ServiceReport::isEstadoActivo)
                .map(ServiceReport::getIdOrdenTrabajo)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public void openDraft(UUID idOrdenTrabajo, ServiceType tipoServicio, UUID idEquipoCliente) {
        if (serviceReportPersistencePort.findActiveByWorkOrderAndEquipment(idOrdenTrabajo, idEquipoCliente)
                .isPresent()) {
            return;
        }

        serviceReportDrafts.open(idOrdenTrabajo, tipoServicio, idEquipoCliente);
    }

    @Override
    @Transactional
    public void retireDraft(UUID idOrdenTrabajo, UUID idEquipoCliente) {
        ServiceReport reporte = serviceReportPersistencePort
                .findActiveByWorkOrderAndEquipment(idOrdenTrabajo, idEquipoCliente)
                .orElse(null);

        if (reporte == null) {
            return;
        }
        if (reporte.getEstado() == ReportState.FINALIZADO) {
            throw new IllegalStateException("El equipo tiene el reporte " + reporte.numero() + " cerrado en esta"
                    + " orden: un servicio prestado no se quita de la orden, y su reporte se sustituye");
        }

        reporte.deactivate();
        serviceReportPersistencePort.save(reporte);
        reporte.pullEvents().forEach(eventDispatcherPort::dispatch);
    }

    /** El número sale del identificador, como el de cualquier reporte. */
    private static ActiveReport comoActivo(ActiveReportSummary reporte) {
        return new ActiveReport(
                reporte.id(),
                reporte.idEquipoCliente(),
                ServiceReport.numeroDe(reporte.id()),
                reporte.estado() == ReportState.FINALIZADO);
    }
}
