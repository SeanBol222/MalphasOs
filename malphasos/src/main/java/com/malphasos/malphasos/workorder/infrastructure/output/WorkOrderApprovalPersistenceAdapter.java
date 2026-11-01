package com.malphasos.malphasos.workorder.infrastructure.output;

import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderApprovalPersistencePort;
import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderApprovalEntity;
import com.malphasos.malphasos.workorder.infrastructure.output.repository.WorkOrderApprovalRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda las aprobaciones. El mapeo es a mano, como en todo agregado: no tiene setters que MapStruct
 * pudiera usar. Sin colecciones, de modo que no hay nada perezoso que exija una transacción abierta.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderApprovalPersistenceAdapter implements WorkOrderApprovalPersistencePort {

    private final WorkOrderApprovalRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkOrderApproval> findActive(UUID idOrden) {
        return repository.findByIdOrdenTrabajoAndEstadoActivoTrue(idOrden).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findApprovedAmong(Collection<UUID> idsOrdenes) {
        // Un «IN ()» vacío no es SQL válido en todos los motores: sin órdenes no hay nada que preguntar.
        return idsOrdenes.isEmpty() ? Set.of() : repository.findApprovedAmong(idsOrdenes);
    }

    @Override
    @Transactional
    public WorkOrderApproval save(WorkOrderApproval aprobacion) {
        WorkOrderApprovalEntity entity = repository.findById(aprobacion.getId()).orElseGet(() -> {
            WorkOrderApprovalEntity nueva = new WorkOrderApprovalEntity();
            nueva.setId(aprobacion.getId());
            nueva.setIdOrdenTrabajo(aprobacion.getIdOrdenTrabajo());
            nueva.setIdSupervisor(aprobacion.getIdSupervisor());
            nueva.setIdOperador(aprobacion.getIdOperador());
            nueva.setHuellaFirma(aprobacion.getHuellaFirma());
            nueva.setAprobada(aprobacion.getAprobada());
            return nueva;
        });
        // Lo único que cambia de una aprobación es que se retire: lo demás es de cuando se dio.
        entity.setEstadoActivo(aprobacion.isEstadoActivo());

        return toDomain(repository.saveAndFlush(entity));
    }

    private WorkOrderApproval toDomain(WorkOrderApprovalEntity entity) {
        return WorkOrderApproval.rehydrate(
                entity.getId(),
                entity.getIdOrdenTrabajo(),
                entity.getIdSupervisor(),
                entity.getIdOperador(),
                entity.getHuellaFirma(),
                entity.getAprobada(),
                entity.isEstadoActivo());
    }
}
