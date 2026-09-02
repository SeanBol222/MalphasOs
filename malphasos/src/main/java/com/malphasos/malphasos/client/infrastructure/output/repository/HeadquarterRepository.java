package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.HeadquarterEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeadquarterRepository extends JpaRepository<HeadquarterEntity, UUID> {

    List<HeadquarterEntity> findByIdCliente(UUID idCliente);
}
