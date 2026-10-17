package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeclareVerificationsCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.CreateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeactivateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.UpdateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre tipos de equipo. */
public interface EquipmentTypeServicePort {

    List<EquipmentType> findAll();

    EquipmentType findById(UUID id);

    EquipmentType create(CreateEquipmentTypeCommand command);

    EquipmentType update(UpdateEquipmentTypeCommand command);

    /** Declara qué se verifica en el tipo, o que deja de verificarse si la lista llega vacía. */
    EquipmentType declareVerifications(DeclareVerificationsCommand command);

    void deactivate(DeactivateEquipmentTypeCommand command);
}
