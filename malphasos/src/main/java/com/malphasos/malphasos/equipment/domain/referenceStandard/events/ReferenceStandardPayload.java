package com.malphasos.malphasos.equipment.domain.referenceStandard.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.time.LocalDate;

/** Lo que un evento de un equipo patrón cuenta de él. */
public record ReferenceStandardPayload(String nombre, String serie, String certificado, LocalDate vencimientoCertificado)
        implements Payload {
}
