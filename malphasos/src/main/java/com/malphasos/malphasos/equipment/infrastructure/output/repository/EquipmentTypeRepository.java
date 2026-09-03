package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentTypeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentTypeEntity, UUID> {
}
