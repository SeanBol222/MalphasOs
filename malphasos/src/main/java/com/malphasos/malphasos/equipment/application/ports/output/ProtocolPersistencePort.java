package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de protocolos. */
public interface ProtocolPersistencePort {

    List<Protocol> findAll();

    Optional<Protocol> findById(UUID id);

    List<Protocol> findAllById(Collection<UUID> ids);

    /** El protocolo vigente con esa clave de nombre, si lo hay. Ver {@link Protocol#clave(String)}. */
    Optional<Protocol> findActiveByClave(String clave);

    /** A cuántos tipos de equipo vigentes está asignado. */
    long countActiveAssignments(UUID idProtocolo);

    Protocol save(Protocol protocolo);
}
