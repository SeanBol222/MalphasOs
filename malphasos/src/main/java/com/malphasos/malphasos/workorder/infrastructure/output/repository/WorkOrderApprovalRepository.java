package com.malphasos.malphasos.workorder.infrastructure.output.repository;

import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderApprovalEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderApprovalRepository extends JpaRepository<WorkOrderApprovalEntity, UUID> {

    /** La vigente. A lo sumo una: lo garantiza el índice único parcial de V54. */
    Optional<WorkOrderApprovalEntity> findByIdOrdenTrabajoAndEstadoActivoTrue(UUID idOrdenTrabajo);

    @Query("""
            select a.idOrdenTrabajo
            from WorkOrderApprovalEntity a
            where a.idOrdenTrabajo in :idsOrdenes
              and a.estadoActivo = true
            """)
    Set<UUID> findApprovedAmong(@Param("idsOrdenes") Collection<UUID> idsOrdenes);
}
