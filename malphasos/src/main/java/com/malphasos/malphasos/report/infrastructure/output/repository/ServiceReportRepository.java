package com.malphasos.malphasos.report.infrastructure.output.repository;

import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceReportRepository extends JpaRepository<ServiceReportEntity, UUID> {

    List<ServiceReportEntity> findByIdOrdenTrabajo(UUID idOrdenTrabajo);

    List<ServiceReportEntity> findByIdEquipoCliente(UUID idEquipoCliente);

    /** Los reportes activos de varias órdenes, solo con lo que los identifica (V50). */
    @Query("""
            SELECT r.id AS id, r.idOrdenTrabajo AS idOrdenTrabajo, r.idEquipoCliente AS idEquipoCliente,
                   r.estado AS estado
            FROM ServiceReportEntity r
            WHERE r.estadoActivo = true AND r.idOrdenTrabajo IN :idsOrdenes
            """)
    List<SummaryRow> findActiveSummaries(Collection<UUID> idsOrdenes);

    /** Una fila de {@link #findActiveSummaries}. */
    interface SummaryRow {
        UUID getId();

        UUID getIdOrdenTrabajo();

        UUID getIdEquipoCliente();

        String getEstado();
    }

    /**
     * Si el equipo tiene un reporte activo en una orden viva sin ejecutar (V50). Nativa y con la orden en
     * el join: el estado de la orden es de {@code work-order}, y preguntárselo a su servicio cerraría un
     * ciclo entre beans —quien pregunta es {@code equipment}, del que depende el servicio de órdenes—. La
     * tabla sí se puede leer: el reporte apunta a ella con su foránea.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1
                           FROM reporte_servicio r
                                    JOIN orden_trabajo o ON o.k_id_orden_trabajo = r.k_id_orden_trabajo
                           WHERE r.k_id_equipo_cliente = :idEquipoCliente
                             AND r.b_estado_activo
                             AND o.b_estado_activo
                             AND o.t_estado_ejecucion <> 'EJECUTADA')
            """, nativeQuery = true)
    boolean inUnexecutedWorkOrder(UUID idEquipoCliente);

    /** Los cerrados sin informe congelado: los que se cerraron antes de V37. */
    List<ServiceReportEntity> findByEstadoAndDocumentoIsNull(String estado);

    /** Si el prefijo ya lo tiene algún reporte. Nativa: es la misma expresión que el índice de V28. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM reporte_servicio"
            + " WHERE left(k_id_reporte_servicio::text, 6) = lower(:numero))", nativeQuery = true)
    boolean existsByNumero(@org.springframework.data.repository.query.Param("numero") String numero);

    /**
     * Por orden: cuántos equipos tienen su reporte vivo cerrado y cuántos en borrador. Una fila por
     * orden, contada en la base y sin cargar ningún reporte; el índice único parcial garantiza un
     * reporte vivo por equipo, así que contar reportes y contar equipos da lo mismo, y se cuentan
     * equipos para no depender de eso.
     */
    @Query("""
            SELECT r.idOrdenTrabajo AS idOrdenTrabajo,
                   COUNT(DISTINCT CASE WHEN r.estado = 'FINALIZADO' THEN r.idEquipoCliente END) AS cerrados,
                   COUNT(DISTINCT CASE WHEN r.estado = 'BORRADOR' THEN r.idEquipoCliente END) AS abiertos
            FROM ServiceReportEntity r
            WHERE r.estadoActivo = true
            GROUP BY r.idOrdenTrabajo
            """)
    List<ProgressRow> progressByWorkOrder();

    /** Una fila de {@link #progressByWorkOrder()}. */
    interface ProgressRow {
        UUID getIdOrdenTrabajo();

        long getCerrados();

        long getAbiertos();
    }

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
