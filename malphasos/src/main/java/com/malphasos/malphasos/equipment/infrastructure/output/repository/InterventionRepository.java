package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.InterventionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterventionRepository extends JpaRepository<InterventionEntity, UUID> {

    /**
     * El historial de un equipo, de lo más reciente a lo más antiguo.
     *
     * <p>El orden va en el nombre del método porque es parte del contrato que RF-27 pide, no una
     * preferencia de quien consulta: así ninguna ruta puede devolverlo desordenado por olvidarse de
     * un {@code Sort}. El índice {@code IX_intervencion_equipo_fecha} está hecho para esto.
     */
    List<InterventionEntity> findByIdEquipoClienteAndEstadoActivoTrueOrderByFechaServicioDesc(
            UUID idEquipoCliente);

    boolean existsByIdReporteServicio(UUID idReporteServicio);
}
