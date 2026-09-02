package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.services.manager.commands.AssignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.DeactivateManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.ReassignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RegisterManagerCommand;
import com.malphasos.malphasos.client.domain.manager.Manager;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre encargados de sedes y áreas de servicio. */
public interface ManagerServicePort {

    List<Manager> findAll();

    /** Se busca por la persona: su identificador es también el del encargado. */
    Manager findByPerson(UUID idPersona);

    List<Manager> findByHeadquarter(UUID idSede);

    List<Manager> findByServiceArea(UUID idArea);

    /** Crea la persona y la pone al frente de una sede o de un área. */
    Manager register(RegisterManagerCommand command);

    /** Pone al frente a alguien que ya existe como persona. */
    Manager assign(AssignManagerCommand command);

    Manager reassign(ReassignManagerCommand command);

    /** Releva al encargado sin borrarlo, conservando el historial. */
    void deactivate(DeactivateManagerCommand command);
}
