package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentCatalogPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.EquipmentTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de tipos de equipo sobre JPA. */
@Component
@RequiredArgsConstructor
public class EquipmentTypePersistenceAdapter implements EquipmentTypePersistencePort {

    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentCatalogPersistenceMapper mapper;

    @Override
    public List<EquipmentType> findAll() {
        return mapper.toEquipmentTypeList(equipmentTypeRepository.findAll());
    }

    @Override
    public Optional<EquipmentType> findById(UUID id) {
        return equipmentTypeRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public EquipmentType save(EquipmentType equipmentType) {
        return mapper.toDomain(equipmentTypeRepository.save(mapper.toEntity(equipmentType)));
    }
}
