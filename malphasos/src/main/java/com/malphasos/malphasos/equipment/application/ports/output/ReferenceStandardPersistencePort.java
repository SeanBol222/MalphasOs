package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El almacén de los equipos patrón. */
public interface ReferenceStandardPersistencePort {

    List<ReferenceStandard> findAll();

    Optional<ReferenceStandard> findById(UUID id);

    Optional<ReferenceStandard> findActiveBySerie(String serie);

    Optional<ReferenceStandard> findActiveByCodigoInterno(String codigoInterno);

    ReferenceStandard save(ReferenceStandard patron);
}
