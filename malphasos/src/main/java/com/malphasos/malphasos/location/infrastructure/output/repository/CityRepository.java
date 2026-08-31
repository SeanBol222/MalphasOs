package com.malphasos.malphasos.location.infrastructure.output.repository;

import com.malphasos.malphasos.location.infrastructure.output.entities.CityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<CityEntity, UUID> {

    List<CityEntity> findByIdPais(UUID idPais);
}
