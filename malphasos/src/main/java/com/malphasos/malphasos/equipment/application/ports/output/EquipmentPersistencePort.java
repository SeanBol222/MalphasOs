package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de asociaciones marca-tipo. */
public interface EquipmentPersistencePort {

    List<Equipment> findAll();

    Optional<Equipment> findById(UUID id);

    List<Equipment> findByBrand(UUID idMarca);

    Equipment save(Equipment equipment);
}
