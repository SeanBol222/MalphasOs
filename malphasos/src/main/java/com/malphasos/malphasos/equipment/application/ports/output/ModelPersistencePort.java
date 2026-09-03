package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.model.Model;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de modelos. */
public interface ModelPersistencePort {

    List<Model> findAll();

    Optional<Model> findById(UUID id);

    List<Model> findByEquipment(UUID idEquipo);

    Model save(Model model);
}
