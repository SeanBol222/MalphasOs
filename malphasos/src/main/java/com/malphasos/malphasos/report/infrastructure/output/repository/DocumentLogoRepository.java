package com.malphasos.malphasos.report.infrastructure.output.repository;

import com.malphasos.malphasos.report.infrastructure.output.entities.DocumentLogoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLogoRepository extends JpaRepository<DocumentLogoEntity, String> {
}
