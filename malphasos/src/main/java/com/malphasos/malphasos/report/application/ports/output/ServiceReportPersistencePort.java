package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
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

    /**
     * El reporte vivo de un equipo en una orden, si lo hay.
     *
     * <p>Existe para que el servicio pueda decir «ese equipo ya tiene reporte» en vez de dejar que
     * salte el índice único de la tabla, que daría un conflicto de integridad sin explicar cuál de
     * las dos columnas choca.
     */
    Optional<ServiceReport> findActiveByWorkOrderAndEquipment(UUID idOrdenTrabajo, UUID idEquipoCliente);
}
