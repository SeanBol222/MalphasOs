package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentChainPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.EquipmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacen sobre JPA. */
@Component
@RequiredArgsConstructor
public class EquipmentPersistenceAdapter implements EquipmentPersistencePort {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentChainPersistenceMapper mapper;

    @Override
    public List<Equipment> findAll() {
        return mapper.toEquipmentList(equipmentRepository.findAll());
    }

    @Override
    public Optional<Equipment> findById(UUID id) {
        return equipmentRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Equipment> findByBrand(UUID idMarca) {
        return mapper.toEquipmentList(equipmentRepository.findByIdMarca(idMarca));
    }

    @Override
    public Equipment save(Equipment equipment) {
        return mapper.toDomain(equipmentRepository.save(mapper.toEntity(equipment)));
    }
}
