package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.equipment.commands.CreateEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.equipment.commands.DeactivateEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso sobre la asociación marca-tipo.
 *
 * <p>Sin operación de cambio: sus dos referencias no se modifican. Si la asociación está mal se
 * retira y se crea la correcta.
 */
public interface EquipmentServicePort {

    List<Equipment> findAll();

    Equipment findById(UUID id);

    List<Equipment> findByBrand(UUID idMarca);

    Equipment create(CreateEquipmentCommand command);

    void deactivate(DeactivateEquipmentCommand command);
}
