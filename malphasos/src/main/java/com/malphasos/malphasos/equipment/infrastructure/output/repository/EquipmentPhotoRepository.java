package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentPhotoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentPhotoRepository extends JpaRepository<EquipmentPhotoEntity, UUID> {
}
