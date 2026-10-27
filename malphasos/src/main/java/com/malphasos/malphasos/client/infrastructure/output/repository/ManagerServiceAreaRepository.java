package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerServiceAreaEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerServiceAreaId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerServiceAreaRepository
        extends JpaRepository<ManagerServiceAreaEntity, ManagerServiceAreaId> {

    List<ManagerServiceAreaEntity> findByPersonaIn(Collection<UUID> personas);

    List<ManagerServiceAreaEntity> findByAreaAndEstadoActivoTrue(UUID area);
}
