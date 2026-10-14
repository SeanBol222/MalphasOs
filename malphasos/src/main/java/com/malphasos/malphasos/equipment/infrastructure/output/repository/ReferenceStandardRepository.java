package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.ReferenceStandardEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceStandardRepository extends JpaRepository<ReferenceStandardEntity, UUID> {

    Optional<ReferenceStandardEntity> findBySerieAndEstadoActivoTrue(String serie);
}
