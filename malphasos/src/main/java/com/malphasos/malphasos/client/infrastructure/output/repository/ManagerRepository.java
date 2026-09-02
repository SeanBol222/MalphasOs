package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerRepository extends JpaRepository<ManagerEntity, UUID> {

    List<ManagerEntity> findByIdSede(UUID idSede);

    List<ManagerEntity> findByIdAreaServicio(UUID idAreaServicio);
}
