package com.malphasos.malphasos.report.infrastructure.output.repository;

import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceReportRepository extends JpaRepository<ServiceReportEntity, UUID> {

    List<ServiceReportEntity> findByIdOrdenTrabajo(UUID idOrdenTrabajo);

    List<ServiceReportEntity> findByIdEquipoCliente(UUID idEquipoCliente);

    /**
     * El reporte vivo de un equipo en una orden.
     *
     * <p>Devuelve uno y no una lista porque el índice único parcial de la tabla garantiza que no hay
     * dos: si algún día hubiera dos, esto lanzaría, y eso es preferible a devolver el primero y
     * seguir como si nada.
     */
    Optional<ServiceReportEntity> findByIdOrdenTrabajoAndIdEquipoClienteAndEstadoActivoTrue(
            UUID idOrdenTrabajo, UUID idEquipoCliente);
}
