package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.DeactivateReferenceStandardCommand;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.SaveReferenceStandardCommand;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import java.util.List;
import java.util.UUID;

/** El catálogo de equipos patrón (2026-10-14). */
public interface ReferenceStandardServicePort {

    /** Vigentes y retirados, por nombre. */
    List<ReferenceStandard> findAll();

    ReferenceStandard findById(UUID id);

    ReferenceStandard create(SaveReferenceStandardCommand command);

    /** Corrige un patrón o renueva su certificado. El identificador del comando dice cuál. */
    ReferenceStandard update(SaveReferenceStandardCommand command);

    void deactivate(DeactivateReferenceStandardCommand command);
}
