package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import java.util.UUID;

/**
 * Declara cómo se verifica un tipo de equipo, o que deja de verificarse si la modalidad es nula.
 *
 * <p>Es una operación aparte porque cambia lo que el tipo es: pasar a ser verificable arrastra los
 * datos metrológicos y las verificaciones que habrá que registrarle.
 */
public record ChangeVerificationModeCommand(UUID id, VerificationMode modalidad) {
}
