package com.malphasos.malphasos.workorder.infrastructure.output.repository;

import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, UUID> {

    List<WorkOrderEntity> findByIdCliente(UUID idCliente);

    List<WorkOrderEntity> findByIdSede(UUID idSede);

    List<WorkOrderEntity> findByIdIngeniero(UUID idIngeniero);

    /**
     * Las órdenes que incluyen un equipo, contando solo las filas del puente que siguen activas.
     *
     * <p>Se escribe a mano porque la derivación por nombre no puede expresar la condición sobre la
     * fila intermedia: un equipo retirado del alcance dejó de estar en esa orden, aunque su fila
     * siga ahí como historial.
     */
    @Query("""
            select distinct o from WorkOrderEntity o
            join o.equipos e
            where e.equipoCliente = :idEquipoCliente and e.estadoActivo = true
            """)
    List<WorkOrderEntity> findByEquipment(UUID idEquipoCliente);
}
