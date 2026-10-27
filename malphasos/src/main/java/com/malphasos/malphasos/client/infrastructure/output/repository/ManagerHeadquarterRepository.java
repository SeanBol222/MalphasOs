package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerHeadquarterEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerHeadquarterId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerHeadquarterRepository
        extends JpaRepository<ManagerHeadquarterEntity, ManagerHeadquarterId> {

    List<ManagerHeadquarterEntity> findByPersonaIn(Collection<UUID> personas);

    List<ManagerHeadquarterEntity> findBySedeAndEstadoActivoTrue(UUID sede);
}
