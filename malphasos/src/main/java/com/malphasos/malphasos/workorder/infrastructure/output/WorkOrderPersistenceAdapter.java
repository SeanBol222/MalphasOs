package com.malphasos.malphasos.workorder.infrastructure.output;

import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderEntity;
import com.malphasos.malphasos.workorder.infrastructure.output.mapper.WorkOrderPersistenceMapper;
import com.malphasos.malphasos.workorder.infrastructure.output.repository.WorkOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guarda y recupera órdenes de trabajo.
 *
 * <p>Al guardar busca antes la fila existente y se la pasa al mapper. No es una lectura de más por
 * pereza: sin ella el mapper construiría una entidad nueva en cada guardado y Hibernate insertaría
 * duplicados del alcance en la tabla puente, que es la misma razón por la que el adaptador de
 * clientes hace lo propio con sus contactos.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderPersistenceAdapter implements WorkOrderPersistencePort {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderPersistenceMapper mapper;

    @Override
    public List<WorkOrder> findAll() {
        return mapper.toDomainList(workOrderRepository.findAll());
    }

    @Override
    public Optional<WorkOrder> findById(UUID id) {
        return workOrderRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<WorkOrder> findByClient(UUID idCliente) {
        return mapper.toDomainList(workOrderRepository.findByIdCliente(idCliente));
    }

    @Override
    public List<WorkOrder> findByHeadquarter(UUID idSede) {
        return mapper.toDomainList(workOrderRepository.findByIdSede(idSede));
    }

    @Override
    public List<WorkOrder> findByEngineer(UUID idIngeniero) {
        return mapper.toDomainList(workOrderRepository.findByIdIngeniero(idIngeniero));
    }

    @Override
    public List<WorkOrder> findByEquipment(UUID idEquipoCliente) {
        return mapper.toDomainList(workOrderRepository.findByEquipment(idEquipoCliente));
    }

    @Override
    public WorkOrder save(WorkOrder orden) {
        WorkOrderEntity existente = workOrderRepository.findById(orden.getId()).orElse(null);

        return mapper.toDomain(workOrderRepository.save(mapper.toEntity(orden, existente)));
    }
}
