package com.malphasos.malphasos.location.infrastructure.output.repository;

import com.malphasos.malphasos.location.infrastructure.output.entities.CountryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<CountryEntity, UUID> {
}
