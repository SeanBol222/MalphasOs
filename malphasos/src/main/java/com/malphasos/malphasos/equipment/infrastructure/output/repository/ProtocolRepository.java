package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.ProtocolEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProtocolRepository extends JpaRepository<ProtocolEntity, UUID> {

    Optional<ProtocolEntity> findByClaveAndEstadoActivoTrue(String clave);

    /**
     * A cuántos tipos de equipo vigentes está asignado un protocolo. Nativa porque la tabla de unión no
     * tiene entidad: la escribe el adaptador de asignaciones con SQL.
     */
    @Query(value = """
            SELECT count(*)
            FROM protocolo_tipo_equipo pt
                     JOIN tipo_equipo t ON t.k_id_tipo_equipo = pt.k_id_tipo_equipo
            WHERE pt.k_id_protocolo = :idProtocolo
              AND pt.b_estado_activo
              AND t.b_estado_activo
            """, nativeQuery = true)
    long countActiveAssignments(UUID idProtocolo);
}
