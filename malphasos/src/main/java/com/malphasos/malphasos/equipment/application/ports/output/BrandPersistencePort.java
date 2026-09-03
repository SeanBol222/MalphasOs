package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.brand.Brand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de marcas. */
public interface BrandPersistencePort {

    List<Brand> findAll();

    Optional<Brand> findById(UUID id);

    Brand save(Brand brand);
}
