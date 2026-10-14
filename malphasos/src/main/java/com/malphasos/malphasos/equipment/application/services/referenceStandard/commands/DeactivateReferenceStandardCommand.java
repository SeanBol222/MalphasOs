package com.malphasos.malphasos.equipment.application.services.referenceStandard.commands;

import java.util.UUID;

/** Retirar un equipo patrón del catálogo. */
public record DeactivateReferenceStandardCommand(UUID id) {
}
