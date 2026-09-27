package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import java.util.UUID;

/**
 * Declara cómo se verifica un tipo de equipo, o que deja de verificarse si la modalidad es nula.
 *
 * <p>Es una operación aparte porque cambia lo que el tipo es: pasar a ser verificable arrastra los
 * datos metrológicos y las verificaciones que habrá que registrarle.
 *
 * <p><b>Lleva los tres datos juntos</b> —modalidad, cuántas lecturas por punto y en qué valores—, y no
 * es por comodidad: por separado existiría el instante en que un tipo dice verificarse contra un patrón
 * constante sin decir contra qué valor, y ese estado no debe poder escribirse.
 */
public record ChangeVerificationModeCommand(
        UUID id,
        VerificationMode modalidad,
        Integer cantidadDatos,
        java.util.List<VerificationPointCommand> puntosVerificacion) {
}
