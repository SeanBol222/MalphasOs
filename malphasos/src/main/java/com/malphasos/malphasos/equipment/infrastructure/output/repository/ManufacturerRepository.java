package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.ManufacturerEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufacturerRepository extends JpaRepository<ManufacturerEntity, UUID> {
}
