package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.model.commands.ChangeModelInvimaCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.CreateModelCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.DeactivateModelCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.RenameModelCommand;
import com.malphasos.malphasos.equipment.domain.model.Model;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre modelos. */
public interface ModelServicePort {

    List<Model> findAll();

    Model findById(UUID id);

    List<Model> findByEquipment(UUID idEquipo);

    Model create(CreateModelCommand command);

    /** Corrige el nombre. Tiene operación propia, igual que renombrar una marca. */
    Model rename(RenameModelCommand command);

    Model changeInvima(ChangeModelInvimaCommand command);

    void deactivate(DeactivateModelCommand command);
}
