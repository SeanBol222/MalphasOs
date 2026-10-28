package com.malphasos.malphasos.shared.infrastructure.output.image.repository;

import com.malphasos.malphasos.shared.infrastructure.output.image.entities.DocumentImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentImageRepository extends JpaRepository<DocumentImageEntity, String> {
}
