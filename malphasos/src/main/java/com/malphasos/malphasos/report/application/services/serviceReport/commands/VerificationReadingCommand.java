package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una lectura tal como llega de fuera.
 *
 * <p><b>Trae la verificación y ya no trae unidad</b> (2026-10-03), y los dos cambios son el mismo
 * cambio. La verificación es obligatoria porque es lo único que dice qué se midió cuando no hay punto;
 * y como la verificación declara su unidad, la unidad deja de hacer falta <b>incluso con patrón y
 * equipo variables</b>, que era el único caso en el que antes tenía que venir de fuera. El campo
 * {@code unidadSinPunto} desapareció con ella.
 *
 * <p>Eso cierra un agujero que quedaba abierto: el llamante podía declarar «°C» en una verificación
 * medida en mmHg y el reporte saldría impreso con una unidad que nadie midió. Ahora la unidad no se
 * puede enviar, así que no se puede contradecir. Es la misma técnica que usa la orden de trabajo con el
 * área de un equipo, que tampoco viene en el comando.
 */
public record VerificationReadingCommand(
        UUID idVerificacion,
        UUID idPuntoVerificacion,
        int secuencia,
        BigDecimal valorPatron,
        BigDecimal valorEquipo) {
}
