package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.BrandEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<BrandEntity, UUID> {
}
