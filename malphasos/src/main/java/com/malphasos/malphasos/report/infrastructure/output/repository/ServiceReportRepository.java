package com.malphasos.malphasos.report.infrastructure.output.repository;

import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceReportRepository extends JpaRepository<ServiceReportEntity, UUID> {

    List<ServiceReportEntity> findByIdOrdenTrabajo(UUID idOrdenTrabajo);

    List<ServiceReportEntity> findByIdEquipoCliente(UUID idEquipoCliente);

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
