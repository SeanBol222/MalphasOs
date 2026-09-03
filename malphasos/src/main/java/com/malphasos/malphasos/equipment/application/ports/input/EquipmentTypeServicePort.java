package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.ChangeVerificationModeCommand;
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

    /** Declara cómo se verifica el tipo, o que deja de verificarse. */
    EquipmentType changeVerificationMode(ChangeVerificationModeCommand command);

    void deactivate(DeactivateEquipmentTypeCommand command);
}
