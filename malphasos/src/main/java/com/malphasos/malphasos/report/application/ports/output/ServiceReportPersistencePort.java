package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.application.model.ActiveReportSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que este módulo necesita de su almacén.
 *
 * <p>No declara {@code delete} ni {@code update}, como ningún puerto de este proyecto: retirar es
 * guardar con el estado en falso.
 */
public interface ServiceReportPersistencePort {

    ServiceReport save(ServiceReport reporte);

    Optional<ServiceReport> findById(UUID id);

    List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo);

    List<ServiceReport> findByEquipment(UUID idEquipoCliente);

    /** Los reportes activos de esas órdenes, sin su contenido (V50): son sus equipos. */
    List<ActiveReportSummary> findActiveSummaries(Collection<UUID> idsOrdenes);

    /**
     * Si el equipo tiene un reporte activo en una orden viva sin ejecutar (V50): mientras lo tenga, no se
     * traslada.
     */
    boolean inUnexecutedWorkOrder(UUID idEquipoCliente);

    /** Si algún reporte, retirado o no, tiene ya ese número: los seis primeros caracteres del id (V28). */
    boolean numberTaken(String numero);

    /** Cuántos equipos con reporte cerrado y en borrador tiene cada orden que tenga alguno vivo. */
    List<WorkOrderReportProgress> progressByWorkOrder();

    /**
     * El reporte vivo de un equipo en una orden, si lo hay.
     *
     * <p>Existe para que el servicio pueda decir «ese equipo ya tiene reporte» en vez de dejar que
     * salte el índice único de la tabla, que daría un conflicto de integridad sin explicar cuál de
     * las dos columnas choca.
     */
    Optional<ServiceReport> findActiveByWorkOrderAndEquipment(UUID idOrdenTrabajo, UUID idEquipoCliente);

    /** Los reportes cerrados, retirados o no, que todavía no tienen su informe congelado (V37). */
    List<ServiceReport> findFinishedWithoutDocument();
}
