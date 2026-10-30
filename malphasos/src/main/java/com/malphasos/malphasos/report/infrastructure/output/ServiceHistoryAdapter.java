package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ServiceHistoryPort;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contesta a {@code equipment} el historial de servicios de un equipo (2026-10-28, {@code V52}): sus reportes
 * cerrados, uno por cada par (orden, equipo).
 *
 * <p><b>La regla de {@code V14}, como filtro.</b> Un reporte cerrado no se edita: se sustituye, y la línea del
 * sustituto reemplaza a la del sustituido cuando se cierra. Así que de cada par cuenta el último reporte
 * cerrado, <b>esté retirado o no</b>: mientras el sustituto es borrador cuenta el sustituido, porque el
 * mantenimiento sí ocurrió; y uno que se retiró sin sustituto, de antes de {@code V50}, sigue contando.
 *
 * <p>Vive aquí porque los reportes son de este módulo, que ya dependía de {@code equipment}, y pregunta a su
 * propia persistencia: el servicio de reportes depende del de órdenes, que depende de {@code equipment}.
 */
@Component
@RequiredArgsConstructor
public class ServiceHistoryAdapter implements ServiceHistoryPort {

    private static final Comparator<ServiceReport> POR_CIERRE = Comparator.comparing(ServiceReport::getFinalizado);

    private final ServiceReportPersistencePort serviceReportPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public List<Intervention> historyOf(UUID idEquipoCliente) {
        Map<UUID, ServiceReport> ultimoPorOrden = serviceReportPersistencePort.findByEquipment(idEquipoCliente).stream()
                .filter(reporte -> reporte.getEstado() == ReportState.FINALIZADO)
                .collect(Collectors.toMap(ServiceReport::getIdOrdenTrabajo, reporte -> reporte,
                        BinaryOperator.maxBy(POR_CIERRE)));

        return ultimoPorOrden.values().stream()
                .sorted(POR_CIERRE.reversed())
                .map(ServiceHistoryAdapter::linea)
                .toList();
    }

    /**
     * La línea de un reporte cerrado. El tipo de servicio es de la orden y el responsable, el ingeniero que la
     * tenía: los dos los congeló el documento del reporte al cerrarse, y todo reporte cerrado tiene el suyo
     * desde {@code V51}.
     */
    private static Intervention linea(ServiceReport reporte) {
        if (reporte.getDocumento() == null) {
            throw new IllegalStateException("El reporte cerrado " + reporte.numero() + " no tiene documento:"
                    + " desde V51 todo reporte cerrado lo tiene");
        }

        return new Intervention(
                reporte.getIdEquipoCliente(),
                reporte.getId(),
                reporte.getFinalizado(),
                InterventionType.valueOf(reporte.getDocumento().orden().tipoServicio()),
                InterventionResult.valueOf(reporte.getResultado().name()),
                reporte.descripcionDelServicio(),
                reporte.getDocumento().ingeniero());
    }
}
