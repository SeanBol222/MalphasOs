package com.malphasos.malphasos.workorder.application.ports.output;

import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Las aprobaciones de las órdenes. Retirar es guardar con el estado en falso: nada se borra. */
public interface WorkOrderApprovalPersistencePort {

    Optional<WorkOrderApproval> findActive(UUID idOrden);

    /** De estas órdenes, las que tienen una aprobación vigente. Una consulta para un listado entero. */
    Set<UUID> findApprovedAmong(Collection<UUID> idsOrdenes);

    WorkOrderApproval save(WorkOrderApproval aprobacion);
}
