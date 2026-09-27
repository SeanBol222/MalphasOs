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
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de tipos de equipo sobre JPA.
 *
 * <p><b>Lleva {@code @Transactional} desde el 2026-09-26</b>, y no es adorno: desde esa fecha el tipo
 * tiene una colección perezosa —sus puntos de verificación— y el mapper la recorre. Con
 * {@code open-in-view} desactivado, sin transacción propia solo funcionaría si el llamante ya hubiera
 * abierto una, que es una dependencia que el tipo no declara. Es exactamente el defecto que
 * {@code work-order} pagó el 2026-09-13, encontrado allí por su primera prueba de persistencia; aquí se
 * pone antes de que ocurra.
 */
@Component
@RequiredArgsConstructor
public class EquipmentTypePersistenceAdapter implements EquipmentTypePersistencePort {

    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentCatalogPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentType> findAll() {
        return mapper.toEquipmentTypeList(equipmentTypeRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentType> findById(UUID id) {
        return equipmentTypeRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public EquipmentType save(EquipmentType equipmentType) {
        return mapper.toDomain(equipmentTypeRepository.save(mapper.toEntity(equipmentType)));
    }
}
