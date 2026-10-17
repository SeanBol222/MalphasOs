package com.malphasos.malphasos.workorder.infrastructure.output.repository;

import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, UUID> {

    List<WorkOrderEntity> findByIdCliente(UUID idCliente);

    /** Si el prefijo ya lo tiene alguna orden. Nativa: es la misma expresión que el índice de V34. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM orden_trabajo"
            + " WHERE left(k_id_orden_trabajo::text, 6) = lower(:numero))", nativeQuery = true)
    boolean existsByNumero(@org.springframework.data.repository.query.Param("numero") String numero);

    List<WorkOrderEntity> findByIdSede(UUID idSede);

    List<WorkOrderEntity> findByIdIngeniero(UUID idIngeniero);

    List<WorkOrderEntity> findByIdClienteIn(Collection<UUID> idsClientes);

    List<WorkOrderEntity> findByIdIngenieroAndIdClienteIn(UUID idIngeniero, Collection<UUID> idsClientes);

    // La agenda: cuatro derivaciones y no una consulta con «:x is null», porque un parámetro UUID nulo
    // no le dice a PostgreSQL de qué tipo es y la consulta falla al prepararse.

    List<WorkOrderEntity> findByFechaMantenimientoBetweenOrderByFechaMantenimientoAsc(
            LocalDate desde, LocalDate hasta);

    List<WorkOrderEntity> findByFechaMantenimientoBetweenAndIdIngenieroOrderByFechaMantenimientoAsc(
            LocalDate desde, LocalDate hasta, UUID idIngeniero);

    List<WorkOrderEntity> findByFechaMantenimientoBetweenAndIdClienteInOrderByFechaMantenimientoAsc(
            LocalDate desde, LocalDate hasta, Collection<UUID> idsClientes);

    List<WorkOrderEntity> findByFechaMantenimientoBetweenAndIdIngenieroAndIdClienteInOrderByFechaMantenimientoAsc(
            LocalDate desde, LocalDate hasta, UUID idIngeniero, Collection<UUID> idsClientes);

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
