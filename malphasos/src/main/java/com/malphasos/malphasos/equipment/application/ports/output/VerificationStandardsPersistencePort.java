package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Almacén de los patrones de cada verificación.
 *
 * <p>Siempre devuelve uno por verificación pedida, aunque esté vacío: una verificación sin patrones tiene
 * un conjunto vacío, no «no tiene».
 */
public interface VerificationStandardsPersistencePort {

    VerificationStandards findByVerification(UUID idVerificacion);

    /** Los de varias verificaciones en una consulta, en el orden en que se piden. */
    List<VerificationStandards> findByVerifications(Collection<UUID> idsVerificacion);

    VerificationStandards save(VerificationStandards patrones);
}
