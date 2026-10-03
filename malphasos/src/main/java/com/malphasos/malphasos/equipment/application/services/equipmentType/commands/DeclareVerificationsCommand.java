package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import java.util.List;
import java.util.UUID;

/**
 * Declara qué se verifica en un tipo de equipo, o que deja de verificarse si la lista llega vacía.
 *
 * <p>Es una operación aparte porque cambia lo que el tipo es: pasar a ser verificable arrastra los
 * datos metrológicos y los reportes que habrá que registrarle.
 *
 * <p><b>Se manda la lista entera.</b> Por separado existiría el instante en que un tipo dice verificar
 * temperatura contra un patrón constante sin decir contra qué valor, y ese estado no debe poder
 * escribirse. Se llamaba {@code ChangeVerificationModeCommand} y llevaba una sola modalidad con sus
 * puntos: dejó de servir el 2026-10-03, cuando un tipo pasó a verificarse en varias magnitudes.
 */
public record DeclareVerificationsCommand(UUID id, List<TypeVerificationCommand> verificaciones) {
}
