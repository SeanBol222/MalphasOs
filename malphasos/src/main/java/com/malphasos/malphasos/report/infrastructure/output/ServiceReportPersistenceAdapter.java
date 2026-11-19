package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import com.malphasos.malphasos.report.infrastructure.output.mapper.ServiceReportPersistenceMapper;
import com.malphasos.malphasos.report.infrastructure.output.repository.ServiceReportRepository;
import com.malphasos.malphasos.report.application.model.ActiveReportSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda y recupera reportes de servicio.
 *
 * <p>Al guardar busca antes la fila existente y se la pasa al mapper, para que Hibernate la ponga al día en
 * vez de intentar insertarla otra vez. Desde V59 el reporte es una sola fila: lo que dice va entero en
 * {@code t_contenido}, sin tablas hijas que conciliar.
 *
 * <p>Los métodos llevan {@code @Transactional} por lo mismo que el resto de adaptadores: la lectura y la
 * escritura de un reporte son una unidad, y los que solo leen van en {@code readOnly}.
 */
@Component
@RequiredArgsConstructor
public class ServiceReportPersistenceAdapter implements ServiceReportPersistencePort {

    private final ServiceReportRepository serviceReportRepository;
    private final ServiceReportPersistenceMapper mapper;

    @Override
    @Transactional
    public ServiceReport save(ServiceReport reporte) {
        ServiceReportEntity existente =
                serviceReportRepository.findById(reporte.getId()).orElse(null);

        return mapper.toDomain(serviceReportRepository.save(mapper.toEntity(reporte, existente)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceReport> findById(UUID id) {
        return serviceReportRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo) {
        return mapper.toDomainList(serviceReportRepository.findByIdOrdenTrabajo(idOrdenTrabajo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrderReportProgress> progressByWorkOrder() {
        return serviceReportRepository.progressByWorkOrder().stream()
                .map(fila -> new WorkOrderReportProgress(
                        fila.getIdOrdenTrabajo(), fila.getCerrados(), fila.getAbiertos()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean numberTaken(String numero) {
        return serviceReportRepository.existsByNumero(numero);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByEquipment(UUID idEquipoCliente) {
        return mapper.toDomainList(serviceReportRepository.findByIdEquipoCliente(idEquipoCliente));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveReportSummary> findActiveSummaries(Collection<UUID> idsOrdenes) {
        if (idsOrdenes.isEmpty()) {
            return List.of();
        }

        return serviceReportRepository.findActiveSummaries(idsOrdenes).stream()
                .map(fila -> new ActiveReportSummary(fila.getId(), fila.getIdOrdenTrabajo(),
                        fila.getIdEquipoCliente(), ReportState.valueOf(fila.getEstado())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean inUnexecutedWorkOrder(UUID idEquipoCliente) {
        return serviceReportRepository.inUnexecutedWorkOrder(idEquipoCliente);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceReport> findActiveByWorkOrderAndEquipment(
            UUID idOrdenTrabajo, UUID idEquipoCliente) {

        return serviceReportRepository
                .findByIdOrdenTrabajoAndIdEquipoClienteAndEstadoActivoTrue(
                        idOrdenTrabajo, idEquipoCliente)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findFinishedWithoutDocument() {
        return mapper.toDomainList(serviceReportRepository.findByEstadoAndDocumentoIsNull(
                ReportState.FINALIZADO.name()));
    }
}
