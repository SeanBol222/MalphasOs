package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.BrandPersistencePort;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentCatalogPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.BrandRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de marcas sobre JPA. */
@Component
@RequiredArgsConstructor
public class BrandPersistenceAdapter implements BrandPersistencePort {

    private final BrandRepository brandRepository;
    private final EquipmentCatalogPersistenceMapper mapper;

    @Override
    public List<Brand> findAll() {
        return mapper.toBrandList(brandRepository.findAll());
    }

    @Override
    public Optional<Brand> findById(UUID id) {
        return brandRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Brand save(Brand brand) {
        return mapper.toDomain(brandRepository.save(mapper.toEntity(brand)));
    }
}
