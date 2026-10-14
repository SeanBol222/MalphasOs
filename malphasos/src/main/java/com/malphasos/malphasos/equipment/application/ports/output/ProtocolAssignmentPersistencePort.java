package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import java.util.List;
import java.util.UUID;

/**
 * Almacén de los protocolos de cada tipo de equipo.
 *
 * <p>Siempre devuelve una asignación, aunque esté vacía: un tipo sin protocolos tiene una lista vacía,
 * no «no tiene asignación».
 */
public interface ProtocolAssignmentPersistencePort {

    ProtocolAssignment findByType(UUID idTipoEquipo);

    /** Las asignaciones vigentes de todos los tipos que tienen alguna, en una consulta. */
    List<ProtocolAssignment> findAllActive();

    ProtocolAssignment save(ProtocolAssignment asignacion);
}
