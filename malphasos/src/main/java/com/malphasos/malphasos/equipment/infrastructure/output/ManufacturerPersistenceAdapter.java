package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ManufacturerPersistencePort;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentCatalogPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ManufacturerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de fabricantes sobre JPA. */
@Component
@RequiredArgsConstructor
public class ManufacturerPersistenceAdapter implements ManufacturerPersistencePort {

    private final ManufacturerRepository manufacturerRepository;
    private final EquipmentCatalogPersistenceMapper mapper;

    @Override
    public List<Manufacturer> findAll() {
        return mapper.toManufacturerList(manufacturerRepository.findAll());
    }

    @Override
    public Optional<Manufacturer> findById(UUID id) {
        return manufacturerRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Manufacturer save(Manufacturer manufacturer) {
        return mapper.toDomain(manufacturerRepository.save(mapper.toEntity(manufacturer)));
    }
}
