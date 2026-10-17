package com.malphasos.malphasos.company.infrastructure.output.repository;

import com.malphasos.malphasos.company.infrastructure.output.entities.ServiceCompanyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCompanyRepository extends JpaRepository<ServiceCompanyEntity, UUID> {

    /** La única fila, si existe. */
    Optional<ServiceCompanyEntity> findFirstBy();
}
