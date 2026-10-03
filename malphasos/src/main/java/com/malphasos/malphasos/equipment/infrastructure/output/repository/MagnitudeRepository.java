package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.MagnitudeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consulta de magnitudes. Sin escrituras: el catálogo entra sembrado por {@code V10}. */
public interface MagnitudeRepository extends JpaRepository<MagnitudeEntity, UUID> {

    /** Las activas, ordenadas por nombre, que es como se ofrecen en una lista. */
    List<MagnitudeEntity> findByEstadoActivoTrueOrderByNombreAsc();
}
