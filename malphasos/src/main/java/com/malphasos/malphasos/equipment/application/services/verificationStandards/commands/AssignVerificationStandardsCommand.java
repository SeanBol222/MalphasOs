package com.malphasos.malphasos.equipment.application.services.verificationStandards.commands;

import java.util.List;
import java.util.UUID;

/** Los patrones de una verificación de un tipo: la lista entera. Vacía, los quita todos. */
public record AssignVerificationStandardsCommand(UUID idTipoEquipo, UUID idVerificacion, List<UUID> patrones) {
}
