package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.ClientEquipmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientEquipmentRepository extends JpaRepository<ClientEquipmentEntity, UUID> {

    List<ClientEquipmentEntity> findByIdAreaServicio(UUID idAreaServicio);
}
