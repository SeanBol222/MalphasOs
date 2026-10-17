package com.malphasos.malphasos.report.infrastructure.output.repository;

import com.malphasos.malphasos.report.infrastructure.output.entities.DocumentImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentImageRepository extends JpaRepository<DocumentImageEntity, String> {
}
