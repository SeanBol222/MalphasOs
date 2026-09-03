package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de tipos de equipo. */
public interface EquipmentTypePersistencePort {

    List<EquipmentType> findAll();

    Optional<EquipmentType> findById(UUID id);

    EquipmentType save(EquipmentType equipmentType);
}
