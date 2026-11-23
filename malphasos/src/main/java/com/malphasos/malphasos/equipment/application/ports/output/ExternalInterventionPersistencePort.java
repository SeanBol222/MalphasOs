package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El almacén de las intervenciones externas (V61). Retirar es guardar con el estado en falso. */
public interface ExternalInterventionPersistencePort {

    Optional<ExternalIntervention> findById(UUID id);

    /** Las vigentes de un equipo, de la más reciente a la más antigua. */
    List<ExternalIntervention> findActiveByEquipment(UUID idEquipoCliente);

    /** Las vigentes de varios equipos a la vez: para el último servicio del listado. */
    List<ExternalIntervention> findActiveByEquipments(Collection<UUID> idsEquiposCliente);

    ExternalIntervention save(ExternalIntervention intervencion);
}
