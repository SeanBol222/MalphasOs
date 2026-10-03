package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentTypeEntity;
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

    /**
     * Guarda en dos pasadas, y la razón está entera en el javadoc del mapper.
     *
     * <p>En resumen: Hibernate vacía los {@code INSERT} antes que los {@code UPDATE}, de modo que al
     * redeclarar la verificación de una magnitud la fila retirada y su sustituta están activas a la vez
     * y el índice único parcial salta. La primera pasada actualiza lo que ya está —incluida la retirada—,
     * el {@code saveAndFlush} la manda a la base, y solo entonces se insertan las nuevas.
     *
     * <p>Es la misma forma que {@code ServiceReportPersistenceAdapter}, que pagó esta trampa primero.
     */
    @Override
    @Transactional
    public EquipmentType save(EquipmentType equipmentType) {
        EquipmentTypeEntity existente =
                equipmentTypeRepository.findById(equipmentType.getId()).orElse(null);
        EquipmentTypeEntity entity = mapper.toEntity(equipmentType, existente);

        if (existente != null) {
            equipmentTypeRepository.saveAndFlush(entity);
        }

        mapper.addNewVerifications(equipmentType, entity);

        return mapper.toDomain(equipmentTypeRepository.save(entity));
    }
}
