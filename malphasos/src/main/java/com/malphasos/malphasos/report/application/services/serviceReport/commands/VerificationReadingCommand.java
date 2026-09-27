package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una lectura tal como llega de fuera.
 *
 * <p><b>No trae unidad</b>, y es deliberado. Cuando la lectura corresponde a un punto de
 * verificación, la unidad es la del punto y el servicio la copia de allí: si el llamante la
 * declarase, podría declarar «°C» en un punto medido en mmHg y el reporte saldría impreso con una
 * unidad que nadie midió. Es la misma técnica que usa la orden de trabajo con el área de un equipo,
 * que tampoco viene en el comando.
 *
 * <p>Con la modalidad de patrón y equipo variables no hay punto del que copiarla, y entonces sí hace
 * falta que venga: para eso está {@link #unidadSinPunto()}.
 */
public record VerificationReadingCommand(
        UUID idPuntoVerificacion,
        int secuencia,
        BigDecimal valorPatron,
        BigDecimal valorEquipo,
        String unidadSinPunto) {
}
