package com.malphasos.malphasos.equipment.application.services.referenceStandard.commands;

import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import java.time.LocalDate;
import java.util.UUID;

/** Alta o cambio de un equipo patrón. En el alta el identificador es nulo. */
public record SaveReferenceStandardCommand(
        UUID id, String nombre, String marca, String serie, String certificado, LocalDate vencimientoCertificado,
        ReferenceStandardSheet ficha) {
}
