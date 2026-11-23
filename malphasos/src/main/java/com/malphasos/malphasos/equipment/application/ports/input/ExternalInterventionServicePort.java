package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.externalIntervention.commands.RecordExternalInterventionCommand;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** Lo que se le hizo a un equipo fuera de MalphasOS (V61): se anota, se lista y se retira. */
public interface ExternalInterventionServicePort {

    /** Las vigentes de un equipo, de la más reciente a la más antigua. */
    List<ExternalIntervention> findByEquipment(UUID idEquipoCliente, ReadScope alcance);

    ExternalIntervention record(RecordExternalInterventionCommand command, ReadScope alcance);

    /** La retira del historial; si no es de ese equipo, «no existe». */
    void deactivate(UUID idEquipoCliente, UUID idIntervencion, ReadScope alcance);
}
