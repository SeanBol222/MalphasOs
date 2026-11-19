package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.verificationStandards.commands.AssignVerificationStandardsCommand;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import java.util.List;
import java.util.UUID;

/** Con qué equipos patrón se hace cada verificación de un tipo de equipo (V60). */
public interface VerificationStandardsServicePort {

    /** Los de cada verificación vigente del tipo, en el orden de sus verificaciones; vacíos si no tiene. */
    List<VerificationStandards> standardsOf(UUID idTipoEquipo);

    VerificationStandards assign(AssignVerificationStandardsCommand command);
}
